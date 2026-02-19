package org.jetbrains.jewel.markdown.extensions.github.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.LayoutDirection.Ltr
import androidx.compose.ui.unit.dp
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.markdown.MarkdownBlock.CustomBlock
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockRendererExtension
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlert.Caution
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlert.Important
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlert.Note
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlert.Tip
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlert.Warning
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text

/** A [MarkdownBlockRendererExtension] that renders [GitHubAlert] blocks. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class GitHubAlertBlockRenderer(private val styling: AlertStyling, private val rootStyling: MarkdownStyling) :
    MarkdownBlockRendererExtension {
    override fun canRender(block: CustomBlock): Boolean = block is GitHubAlert

    @Composable
    override fun RenderCustomBlock(
        block: CustomBlock,
        blockRenderer: MarkdownBlockRenderer,
        inlineRenderer: InlineMarkdownRenderer,
        enabled: Boolean,
        modifier: Modifier,
        onUrlClick: (String) -> Unit,
    ) {
        // Smart cast doesn't work in this case, and then the detection for redundant suppression is
        // also borked
        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression") // ktfmt: break line
        val alert = block as? GitHubAlert

        when (alert) {
            is Caution -> Alert(alert, styling.caution, enabled, blockRenderer, onUrlClick, modifier)
            is Important -> Alert(alert, styling.important, enabled, blockRenderer, onUrlClick, modifier)
            is Note -> Alert(alert, styling.note, enabled, blockRenderer, onUrlClick, modifier)
            is Tip -> Alert(alert, styling.tip, enabled, blockRenderer, onUrlClick, modifier)
            is Warning -> Alert(alert, styling.warning, enabled, blockRenderer, onUrlClick, modifier)
            else -> error("Unsupported block of type ${block.javaClass.name} cannot be rendered")
        }
    }

    @Composable
    private fun Alert(
        block: GitHubAlert,
        styling: BaseAlertStyling,
        enabled: Boolean,
        blockRenderer: MarkdownBlockRenderer,
        onUrlClick: (String) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Column(
            modifier
                .drawBehind {
                    val isLtr = layoutDirection == Ltr
                    val lineWidthPx = styling.lineWidth.toPx()
                    val x = if (isLtr) lineWidthPx / 2 else size.width - lineWidthPx / 2

                    drawLine(
                        color = styling.lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = lineWidthPx,
                        cap = styling.strokeCap,
                        pathEffect = styling.pathEffect,
                    )
                }
                .padding(styling.padding),
            verticalArrangement = Arrangement.spacedBy(rootStyling.blockVerticalSpacing),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val titleIconKey = styling.titleIconKey
                if (titleIconKey != null) {
                    Icon(
                        key = titleIconKey,
                        contentDescription = null,
                        iconClass = AlertStyling::class.java,
                        tint = styling.titleIconTint,
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
                blockRenderer.RenderBlocks(block.content, enabled, onUrlClick, Modifier)
            }
        }
    }
}
