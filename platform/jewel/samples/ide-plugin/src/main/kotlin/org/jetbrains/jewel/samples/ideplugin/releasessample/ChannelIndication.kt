package org.jetbrains.jewel.samples.ideplugin.releasessample

import com.intellij.openapi.ui.GraphicsConfig
import com.intellij.ui.paint.RectanglePainter
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JLabel

internal class ChannelIndication(private val channel: ReleaseChannel) : JLabel(channel.name.lowercase()) {
    init {
        border = JBUI.Borders.empty(2, 4)
        foreground = channel.foreground // TODO why does this not work UGGHHH
        isOpaque = false
        font = JBFont.medium()
    }

    override fun paint(g: Graphics?) {
        with(g as Graphics2D) {
            val graphicsConfig = GraphicsConfig(this)
            graphicsConfig.setupRoundedBorderAntialiasing()

            RectanglePainter.paint(this, x, y, width, height - y, JBUIScale.scale(8), channel.background, null)

            graphicsConfig.restore()
        }
        super.paint(g)
    }
}
