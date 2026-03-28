package com.equationl.ncnndemo2

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.equationl.ncnnandroidppocr.OCR
import com.equationl.ncnnandroidppocr.bean.Device
import com.equationl.ncnnandroidppocr.bean.DrawModel
import com.equationl.ncnnandroidppocr.bean.ImageSize
import com.equationl.ncnnandroidppocr.bean.ModelType
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    private val TAG = "el, Main"

    private lateinit var ocr: OCR
    private var isModelInitialized = false
    private var lastRecognizedText = ""

    private lateinit var resultImg: ImageView
    private lateinit var resultText: TextView
    private lateinit var statusText: TextView
    private lateinit var copyBtn: Button
    private lateinit var selectImageBtn: Button

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            openGallery()
        } else {
            Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            processSelectedImage(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ocr = OCR()

        val initBtn = findViewById<Button>(R.id.init_model)
        selectImageBtn = findViewById<Button>(R.id.select_image)
        copyBtn = findViewById<Button>(R.id.copy_text)
        resultImg = findViewById(R.id.result_img)
        resultText = findViewById(R.id.result_text)
        statusText = findViewById(R.id.status_text)

        initBtn.setOnClickListener {
            initModel()
        }

        selectImageBtn.setOnClickListener {
            if (!isModelInitialized) {
                Toast.makeText(this, "请先初始化模型", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkPermissionAndOpenGallery()
        }

        copyBtn.setOnClickListener {
            copyResultToClipboard()
        }
    }

    private fun initModel() {
        statusText.text = "正在加载模型..."
        val initResult = ocr.initModelFromAssert(assets, ModelType.Mobile, ImageSize.Size720, Device.CPU)

        if (initResult) {
            isModelInitialized = true
            statusText.text = "模型加载成功，可以开始识别"
            selectImageBtn.isEnabled = true
            Log.i(TAG, "onSuccess: 初始化成功")
            Toast.makeText(this, "模型加载成功", Toast.LENGTH_SHORT).show()
        } else {
            statusText.text = "模型加载失败"
            Log.e(TAG, "onFail: 初始化失败")
            Toast.makeText(this, "模型加载失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionAndOpenGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                == PackageManager.PERMISSION_GRANTED) {
                openGallery()
            } else {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
                openGallery()
            } else {
                requestPermissionLauncher.launch(arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ))
            }
        }
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun processSelectedImage(uri: Uri) {
        statusText.text = "正在识别..."

        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                statusText.text = "无法读取图片"
                return
            }

            resultImg.setImageBitmap(bitmap)
            runOcr(bitmap)
        } catch (e: Exception) {
            statusText.text = "读取图片失败: ${e.message}"
            Log.e(TAG, "processSelectedImage: ", e)
        }
    }

    private fun runOcr(bitmap: Bitmap) {
        val result = ocr.detectBitmap(bitmap, drawModel = DrawModel.Full)
        
        if (result == null) {
            statusText.text = "识别失败"
            resultText.text = ""
            copyBtn.isEnabled = false
            Log.e(TAG, "runOcr: 识别失败！")
            return
        }

        val simpleText = result.text
        val inferenceTime = result.inferenceTime
        val outputRawResult = result.textLines

        lastRecognizedText = simpleText

        val displayText = StringBuilder()
        displayText.append("识别结果：\n")
        displayText.append(simpleText)
        displayText.append("\n\n识别时间: ${inferenceTime}ms")
        displayText.append("\n识别到 ${outputRawResult.size} 个文本区域")

        resultText.text = displayText.toString()
        resultImg.setImageBitmap(result.drawBitmap ?: bitmap)
        statusText.text = "识别完成"
        copyBtn.isEnabled = true

        outputRawResult.forEachIndexed { index, ocrResultModel ->
            Log.i(TAG, "runOcr: text = ${ocrResultModel.text}")
        }
    }

    private fun copyResultToClipboard() {
        if (lastRecognizedText.isEmpty()) {
            Toast.makeText(this, "没有可复制的内容", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OCR识别结果", lastRecognizedText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        ocr.release()
    }
}
