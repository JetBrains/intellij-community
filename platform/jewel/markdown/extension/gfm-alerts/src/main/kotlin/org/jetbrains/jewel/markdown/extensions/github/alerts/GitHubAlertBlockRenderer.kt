package org.jetbrains.jewel.markdown.extensions.github.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.LayoutDirection.Ltr
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.markdown.MarkdownBlock.CustomBlock
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockRendererExtension
import org.jetbrains.jewel.markdown.extensions.github.alerts.Alert.Caution
import org.jetbrains.jewel.markdown.extensions.github.alerts.Alert.Important
import org.jetbrains.jewel.markdown.extensions.github.alerts.Alert.Note
import org.jetbrains.jewel.markdown.extensions.github.alerts.Alert.Tip
import org.jetbrains.jewel.markdown.extensions.github.alerts.Alert.Warning
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text

public class GitHubAlertBlockRenderer(private val styling: AlertStyling, private val rootStyling: MarkdownStyling) :
    MarkdownBlockRendererExtension {
    override fun canRender(block: CustomBlock): Boolean = block is Alert

    @Composable
    override fun render(
        block: CustomBlock,
        blockRenderer: MarkdownBlockRenderer,
        inlineRenderer: InlineMarkdownRenderer,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    ) {
        // Smart cast doesn't work in this case, and then the detection for redundant suppression is
        // also borked
        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression") val alert = block as? Alert

        when (alert) {
            is Caution -> Alert(alert, styling.caution, enabled, blockRenderer, onUrlClick, onTextClick)
            is Important -> Alert(alert, styling.important, enabled, blockRenderer, onUrlClick, onTextClick)
            is Note -> Alert(alert, styling.note, enabled, blockRenderer, onUrlClick, onTextClick)
            is Tip -> Alert(alert, styling.tip, enabled, blockRenderer, onUrlClick, onTextClick)
            is Warning -> Alert(alert, styling.warning, enabled, blockRenderer, onUrlClick, onTextClick)
            else -> error("Unsupported block of type ${block.javaClass.name} cannot be rendered")
        }
    }

    @Composable
    private fun Alert(
        block: Alert,
        styling: BaseAlertStyling,
        enabled: Boolean,
        blockRenderer: MarkdownBlockRenderer,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    ) {
        Column(
            Modifier.drawBehind {
                    val isLtr = layoutDirection == Ltr
                    val lineWidthPx = styling.lineWidth.toPx()
                    val x = if (isLtr) lineWidthPx / 2 else size.width - lineWidthPx / 2

                    drawLine(
                        styling.lineColor,
                        Offset(x, 0f),
                        Offset(x, size.height),
                        lineWidthPx,
                        styling.strokeCap,
                        styling.pathEffect,
                    )
                }
                .padding(styling.padding),
            verticalArrangement = Arrangement.spacedBy(rootStyling.blockVerticalSpacing),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val titleIconProvider = styling.titleIconKey
                if (titleIconProvider != null) {
                    val colorFilter =
                        remember(styling.titleIconTint) {
                            if (styling.titleIconTint.isSpecified) {
                                ColorFilter.tint(styling.titleIconTint)
                            } else {
                                null
                            }
                        }

                    Icon(
                        titleIconProvider,
                        contentDescription = null,
                        iconClass = AlertStyling::class.java,
                        colorFilter = colorFilter,
                    )
                }

                CompositionLocalProvider(
                    LocalContentColor provides styling.titleTextStyle.color.takeOrElse { LocalContentColor.current }
                ) {
                    Text(
                        text = block.javaClass.simpleName,
                        style = styling.titleTextStyle,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Default, overrideDescendants = true),
                    )
                }
            }
            CompositionLocalProvider(
                LocalContentColor provides styling.textColor.takeOrElse { LocalContentColor.current }
            ) {
                blockRenderer.render(block.content, enabled, onUrlClick, onTextClick)
            }
        }
    }
}
