package org.jetbrains.jewel.samples.ideplugin

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.COLUMNS_SHORT
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.IdeSampleIconKeys
import icons.JewelIcons
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.bridge.medium
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.theme.textAreaStyle

internal class SwingComparisonTabPanel : BorderLayoutPanel() {
    private val mainContent =
        panel {
            buttonsRow()
            separator()
            labelsRows()
            separator()
            iconsRow()
            separator()
            textFieldsRow()
            separator()
            textAreasRow()
        }.apply {
            border = JBUI.Borders.empty(0, 10)
            isOpaque = false
        }

    private val scrollingContainer =
        JBScrollPane(
            mainContent,
            JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
        )

    init {
        addToCenter(scrollingContainer)
        scrollingContainer.border = null
        scrollingContainer.isOpaque = false
        isOpaque = false
    }

    private fun Panel.buttonsRow() {
        row("Buttons:") {
            button("Swing Button") {}.align(AlignY.CENTER)
            compose { OutlinedButton({}) { Text("Compose Button") } }

            button("Default Swing Button") {}.align(AlignY.CENTER)
                .applyToComponent { putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true) }
            compose { DefaultButton({}) { Text("Default Compose Button") } }
        }.layout(RowLayout.PARENT_GRID)
    }

    private fun Panel.labelsRows() {
        row("Labels:") {
            label("Swing label").align(AlignY.CENTER)
            compose { Text("Compose label") }
        }.layout(RowLayout.PARENT_GRID)

        row("Comments:") {
            comment("Swing comment").align(AlignY.CENTER)
            compose {
                Text("Compose comment", style = Typography.medium(), color = JewelTheme.globalColors.text.info)
            }
        }.layout(RowLayout.PARENT_GRID)
    }

    private fun Panel.iconsRow() {
        row("Icons:") {
            cell(
                JBLabel(JewelIcons.ToolWindowIcon).apply { border = JBUI.Borders.customLine(JBColor.RED) },
            ).align(AlignY.CENTER)

            compose {
                Icon(
                    key = IdeSampleIconKeys.jewelToolWindow,
                    contentDescription = null,
                    modifier = Modifier.border(1.dp, Color.Red),
                )
            }
        }.layout(RowLayout.PARENT_GRID)
    }

    private fun Panel.textFieldsRow() {
        row("Text fields:") {
            textField().align(AlignY.CENTER)

            compose {
                val state = rememberTextFieldState("")
                TextField(state)
            }
        }.layout(RowLayout.PARENT_GRID)
    }

    private fun Panel.textAreasRow() {
        row("Text areas:") {
            textArea().align(AlignY.CENTER).applyToComponent { rows = 3 }

            compose {
                val metrics = remember(JBFont.label(), LocalDensity.current) { getFontMetrics(JBFont.label()) }
                val charWidth =
                    remember(metrics.widths) {
                        // Same logic as in JTextArea
                        metrics.charWidth('m')
                    }
                val lineHeight = metrics.height

                val width = remember(charWidth) { (COLUMNS_SHORT * charWidth) }
                val height = remember(lineHeight) { (3 * lineHeight) }

                val contentPadding = JewelTheme.textAreaStyle.metrics.contentPadding
                val state = rememberTextFieldState("Hello")
                TextArea(
                    state = state,
                    modifier =
                        Modifier.size(
                            width = width.dp + contentPadding.horizontal(LocalLayoutDirection.current),
                            height = height.dp + contentPadding.vertical(),
                        ),
                )
            }
        }.layout(RowLayout.PARENT_GRID)
    }

    private fun PaddingValues.vertical(): Dp = calculateTopPadding() + calculateBottomPadding()

    private fun PaddingValues.horizontal(layoutDirection: LayoutDirection): Dp =
        calculateStartPadding(layoutDirection) + calculateEndPadding(layoutDirection)

    private fun Row.compose(content: @Composable () -> Unit) =
        cell(
            JewelComposePanel {
                Box(Modifier.padding(8.dp)) { content() }
            }.apply { isOpaque = false },
        )
}
