package org.jetbrains.jewel.samples.ideplugin.releasessample

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.GraphicsConfig
import com.intellij.ui.util.maximumHeight
import com.intellij.ui.util.maximumWidth
import com.intellij.util.ui.ImageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.image.BufferedImage
import javax.swing.JComponent

// TODO: figure out how to show a placeholder while the image is being loaded,
//  while avoiding infinite loops of resized -> updateScaledImage() ->
//  getPreferredSize() -> resized -> ...
internal class ImageComponent(
    private val scope: CoroutineScope,
    bufferedImage: BufferedImage? = null,
) : JComponent() {

    private var resizeJob: Job? = null

    var image: BufferedImage? = bufferedImage
        set(value) {
            if (field == value) return
            field = value
            updateScaledImage()
        }

    private var scaledImage: Image? = null

    init {
        addComponentListener(object : ComponentListener {
            override fun componentResized(e: ComponentEvent?) {
                updateScaledImage()
            }

            override fun componentMoved(e: ComponentEvent?) {
                // No-op
            }

            override fun componentShown(e: ComponentEvent?) {
                // No-op
            }

            override fun componentHidden(e: ComponentEvent?) {
                // No-op
            }
        })

        registerUiInspectorInfoProvider {
            mapOf(
                "image" to image,
                "imageSize" to image?.let { Dimension(ImageUtil.getUserWidth(it), ImageUtil.getUserHeight(it)) },
            )
        }
    }

    @Suppress("InjectDispatcher") // It's ok in a silly sample
    private fun updateScaledImage() {
        resizeJob?.cancel("New image")

        val currentImage = image ?: return

        resizeJob = scope.launch(Dispatchers.Default) {
            val imageWidth = currentImage.width

            val componentWidth = width
            val ratioToFit = componentWidth.toDouble() / imageWidth

            val newImage = ImageUtil.scaleImage(currentImage, ratioToFit)

            launch(Dispatchers.EDT) {
                scaledImage = newImage
                revalidate()
            }
        }
    }

    override fun getPreferredSize(): Dimension {
        val currentImage = scaledImage

        return if (!isPreferredSizeSet && currentImage != null) {
            val dimension = Dimension(
                ImageUtil.getRealWidth(currentImage).coerceAtMost(maximumWidth),
                ImageUtil.getRealHeight(currentImage).coerceAtMost(maximumHeight),
            )
            dimension
        } else {
            super.getPreferredSize()
        }
    }

    override fun paintComponent(g: Graphics) {
        val currentImage = scaledImage ?: return

        with(g as Graphics2D) {
            val graphicsConfig = GraphicsConfig(this)
            graphicsConfig.setupAAPainting()

            val imageWidth = ImageUtil.getUserWidth(currentImage)
            val imageHeight = ImageUtil.getUserHeight(currentImage)

            val componentWidth = width
            val componentHeight = height

            drawImage(
                /* img = */
                currentImage,
                /* x = */
                componentWidth / 2 - (imageWidth) / 2,
                /* y = */
                componentHeight / 2 - (imageHeight) / 2,
                /* observer = */
                null,
            )

            graphicsConfig.restore()
        }
    }

    override fun removeNotify() {
        super.removeNotify()
        resizeJob?.cancel("Detaching")
    }
}
