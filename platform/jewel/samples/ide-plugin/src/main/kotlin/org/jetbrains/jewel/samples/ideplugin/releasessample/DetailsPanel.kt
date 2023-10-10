package org.jetbrains.jewel.samples.ideplugin.releasessample

import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.util.maximumHeight
import com.intellij.util.ImageLoader
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.toJavaLocalDate
import java.awt.BorderLayout
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.swing.ScrollPaneConstants

internal class DetailsPanel(private val scope: CoroutineScope) : JBPanelWithEmptyText(BorderLayout()), ComponentWithEmptyText {

    fun display(contentItem: ContentItem?) {
        removeAll()

        val content = when (contentItem) {
            is ContentItem.AndroidRelease -> ItemDetailsPanel(contentItem, scope)
            is ContentItem.AndroidStudio -> ItemDetailsPanel(contentItem, scope)
            null -> return
        }
        add(content, BorderLayout.CENTER)
    }
}

private class ItemDetailsPanel(
    contentItem: ContentItem,
    scope: CoroutineScope,
) : BorderLayoutPanel() {

    private val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    init {
        val bufferedImage = contentItem.imagePath
            ?.let { ImageLoader.loadFromResource(it, javaClass) }
            ?.let { ImageUtil.toBufferedImage(it) }

        if (bufferedImage != null) {
            val imageContainer = ImageComponent(scope).apply {
                maximumHeight = scale(200)
                image = bufferedImage
            }

            addToTop(imageContainer)
        }

        // Using the Kotlin DSL v2 to make this less painful
        val mainContentPanel = panel {
            commonContent(contentItem)

            when (contentItem) {
                is ContentItem.AndroidRelease -> androidReleaseContent(contentItem)
                is ContentItem.AndroidStudio -> androidStudioContent(contentItem)
            }
        }
        mainContentPanel.border = JBUI.Borders.empty(12, 20)

        val scrollingContainer =
            JBScrollPane(
                mainContentPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
            )

        addToCenter(scrollingContainer)
    }

    private fun Panel.commonContent(contentItem: ContentItem) {
        row {
            text(contentItem.displayText)
                .let {
                    val releaseDate = contentItem.releaseDate
                    if (releaseDate != null) {
                        it.comment("Released on ${formatter.format(releaseDate.toJavaLocalDate())}")
                    } else {
                        it
                    }
                }
                .component.font = JBFont.h1()
        }.bottomGap(BottomGap.MEDIUM)
    }

    private fun Panel.androidReleaseContent(contentItem: ContentItem.AndroidRelease) {
        row {
            label("Codename:")
            text(contentItem.codename ?: "N/A").bold()
        }
        row {
            label("Version:")
            text(contentItem.versionName).bold()
        }
        row {
            label("API level:")
            text(contentItem.apiLevel.toString()).bold()
        }
    }

    private fun Panel.androidStudioContent(contentItem: ContentItem.AndroidStudio) {
        row {
            label("Channel:")
            text(contentItem.channel.name).bold()
        }
        row {
            label("Version:")
            text(contentItem.versionName).bold()
        }
        row {
            label("IntelliJ Platform version:")
            text(contentItem.platformVersion).bold()
        }
        row {
            label("IntelliJ Platform build:")
            text(contentItem.platformBuild).bold()
        }
        row {
            label("Full build number:")
            text(contentItem.build).bold()
        }
    }
}
