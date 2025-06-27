package org.jetbrains.jewel.samples.ideplugin

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.components.BrowserLink
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
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel
import javax.swing.JPanel
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.bridge.retrieveEditorColorScheme
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.ExternalLink
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.disabledAppearance
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.textAreaStyle
import org.jetbrains.jewel.ui.typography

internal class SwingComparisonTabPanel : BorderLayoutPanel() {
    private val mainContent =
        panel {
                separator()
                buttonsRow()
                separator()
                labelsRows()
                separator()
                iconsRow()
                separator()
                linksRow()
                separator()
                textFieldsRow()
                separator()
                textAreasRow()
                separator()
                comboBoxesRow()
                separator()
            }
            .apply {
                border = JBUI.Borders.empty(0, 10)
                isOpaque = false
            }

    private fun Panel.linksRow() {
        val jewelReadmeLink = "https://github.com/JetBrains/intellij-community/tree/master/platform/jewel/#readme"

        row("Links:") {
                cell(
                    component =
                        BrowserLink(
                                icon = AllIcons.Ide.External_link_arrow,
                                text = "Enabled link",
                                tooltip = null,
                                url = "",
                            )
                            .apply { enabled(true) }
                )

                compose { ExternalLink(text = "Enabled link", uri = "", enabled = true) }

                cell(
                        component =
                            BrowserLink(
                                    icon = IconLoader.getDisabledIcon(AllIcons.Ide.External_link_arrow),
                                    text = "Disabled link",
                                    tooltip = null,
                                    url = jewelReadmeLink,
                                )
                                .apply { isEnabled = false }
                    )
                    .applyToComponent { autoHideOnDisable = false }

                compose { ExternalLink(text = "Disabled link", uri = "", enabled = false) }
            }
            .layout(RowLayout.PARENT_GRID)
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

                button("Default Swing Button") {}
                    .align(AlignY.CENTER)
                    .applyToComponent { putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true) }
                compose { DefaultButton({}) { Text("Default Compose Button") } }
            }
            .layout(RowLayout.PARENT_GRID)
    }

    private fun Panel.labelsRows() {
        row("Labels:") {
                label("Swing label").align(AlignY.CENTER)
                compose { Text("Compose label") }
            }
            .layout(RowLayout.PARENT_GRID)

        row("Comments:") {
                comment("Swing comment").align(AlignY.CENTER)
                compose {
                    Text(
                        "Compose comment",
                        style = JewelTheme.typography.medium,
                        color = JewelTheme.globalColors.text.info,
                    )
                }
            }
            .layout(RowLayout.PARENT_GRID)

        val longText = "WordWrapInsideWordsIsSupported:" + ("NoSpace".repeat(20) + " ").repeat(5) + "End"
        row("Long text (Swing)") { text(longText, maxLineLength = 100) }
        row("Long text (Compose)") {
            compose {
                Box {
                    Text(
                        longText,
                        modifier =
                            Modifier.width(
                                with(LocalDensity.current) {
                                    // Guesstimate how wide this should be ? we can't tell it to be
                                    // "fill", as it crashes natively
                                    JewelTheme.defaultTextStyle.fontSize.toDp() * 60
                                }
                            ),
                    )
                }
            }
        }

        row("Long editor text (Swing)") {
            text(longText, maxLineLength = 100).applyToComponent {
                font = retrieveEditorColorScheme().getFont(EditorFontType.PLAIN)
            }
        }
        row("Long editor text (Compose)") {
            compose {
                Box {
                    Text(
                        longText,
                        style = JewelTheme.editorTextStyle,
                        modifier =
                            Modifier.width(
                                with(LocalDensity.current) {
                                    // Guesstimate how wide this should be ? we can't tell it to be
                                    // "fill", as it crashes natively
                                    JewelTheme.defaultTextStyle.fontSize.toDp() * 60
                                }
                            ),
                    )
                }
            }
        }

        row("Titles (Swing)") {
            text("This will wrap over a couple rows", maxLineLength = 30).component.font = JBFont.h1()
        }
        row("Titles (Compose)") {
            compose {
                Box {
                    val style = JewelTheme.typography.h1TextStyle
                    Text(
                        "This will wrap over a couple rows",
                        modifier =
                            Modifier.width(
                                with(LocalDensity.current) {
                                    // Guesstimate how wide this should be ? we can't tell it to be
                                    // "fill", as it crashes natively
                                    style.fontSize.toDp() * 10
                                }
                            ),
                        style = style,
                    )
                }
            }
        }
    }

    private fun Panel.iconsRow() {
        row("Icons:") {
                cell(JBLabel(JewelIcons.ToolWindowIcon).apply { border = JBUI.Borders.customLine(JBColor.RED) })
                    .align(AlignY.CENTER)

                compose {
                    Icon(
                        key = IdeSampleIconKeys.jewelToolWindow,
                        contentDescription = "Jewel Tool Window Icon",
                        modifier = Modifier.border(1.dp, Color.Red),
                    )
                }

                panel {
                    row {
                        label("Swing").widthGroup("swing disabled")
                        label("Compose").widthGroup("compose disabled")
                        label("Swing").widthGroup("swing enabled")
                        label("Compose").widthGroup("compose enabled")

                        label("Swing").widthGroup("swing disabled")
                        label("Compose").widthGroup("compose disabled")
                        label("Swing").widthGroup("swing enabled")
                        label("Compose").widthGroup("compose enabled")
                    }
                    row {
                        icon(icon = IconLoader.getDisabledIcon(AllIcons.Actions.CheckOut)).widthGroup("swing disabled")
                        compose {
                                Icon(
                                    modifier = Modifier.disabledAppearance(),
                                    key = AllIconsKeys.Actions.CheckOut,
                                    contentDescription = null,
                                )
                            }
                            .widthGroup("compose disabled")
                        icon(icon = AllIcons.Actions.CheckOut).widthGroup("swing enabled")
                        compose {
                                Icon(
                                    key = AllIconsKeys.Actions.CheckOut,
                                    contentDescription = null,
                                    colorFilter = null, // Not disabled
                                )
                            }
                            .widthGroup("compose enabled")

                        icon(icon = IconLoader.getDisabledIcon(AllIcons.Actions.Close)).widthGroup("swing disabled")
                        compose {
                                Icon(
                                    modifier = Modifier.disabledAppearance(),
                                    key = AllIconsKeys.Actions.Close,
                                    contentDescription = null,
                                )
                            }
                            .widthGroup("compose disabled")
                        icon(icon = AllIcons.Actions.Close).widthGroup("swing enabled")
                        compose {
                                Icon(
                                    key = AllIconsKeys.Actions.Close,
                                    contentDescription = null,
                                    colorFilter = null, // Not disabled
                                )
                            }
                            .widthGroup("compose enabled")
                    }
                }
            }
            .layout(RowLayout.PARENT_GRID)
    }

    private fun Panel.textFieldsRow() {
        row("Text fields:") {
                textField().align(AlignY.CENTER)

                compose {
                    val state = rememberTextFieldState("")
                    TextField(state)
                }
            }
            .layout(RowLayout.PARENT_GRID)
    }

    private fun Panel.comboBoxesRow() {
        row("Combo Boxes:") {
                // Swing ComboBoxes
                val zoomLevels = arrayOf("100%", "125%", "150%", "175%", "200%", "300%")

                JPanel()
                    .apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        add(JLabel("Not editable").apply { alignmentX = LEFT_ALIGNMENT })
                        add(
                            ComboBox(DefaultComboBoxModel(zoomLevels)).apply {
                                isEditable = false
                                alignmentX = LEFT_ALIGNMENT
                            }
                        )
                    }
                    .run { cell(this).align(AlignY.TOP) }
                JPanel()
                    .apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        add(JLabel("Not editable + disabled").apply { alignmentX = LEFT_ALIGNMENT })
                        add(
                            ComboBox(DefaultComboBoxModel(zoomLevels)).apply {
                                isEditable = false
                                isEnabled = false
                                alignmentX = LEFT_ALIGNMENT
                            }
                        )
                    }
                    .run { cell(this).align(AlignY.TOP) }

                val itemsComboBox = arrayOf("Cat", "Elephant", "Sun", "Book", "Laughter")
                JPanel()
                    .apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        add(JLabel("Editable").apply { alignmentX = LEFT_ALIGNMENT })
                        add(
                            ComboBox(DefaultComboBoxModel(itemsComboBox)).apply {
                                isEditable = true
                                alignmentX = LEFT_ALIGNMENT
                            }
                        )
                    }
                    .run { cell(this).align(AlignY.TOP) }

                JPanel()
                    .apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        add(JLabel("Editable + Disabled").apply { alignmentX = LEFT_ALIGNMENT })
                        add(
                            ComboBox(DefaultComboBoxModel(itemsComboBox)).apply {
                                isEditable = true
                                isEnabled = false
                                alignmentX = LEFT_ALIGNMENT
                            }
                        )
                    }
                    .run { cell(this).align(AlignY.TOP) }

                compose(modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp)) {
                    val comboBoxItems = remember {
                        listOf(
                            "Cat",
                            "Elephant",
                            "Sun",
                            "Book",
                            "Laughter",
                            "Whisper",
                            "Ocean",
                            "Serendipity lorem ipsum",
                            "Umbrella",
                            "Joy",
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column {
                            var selectedIndex by remember { mutableIntStateOf(0) }
                            val selectedText: String = comboBoxItems[selectedIndex]

                            Text("Not editable")
                            Text(text = "Selected item: $selectedText")

                            //                            ListComboBox(
                            //                                items = comboBoxItems,
                            //                                selectedIndex = selectedIndex,
                            //                                onSelectedItemChange = { selectedIndex = it },
                            //                                modifier = Modifier.width(200.dp),
                            //                            )
                        }

                        Column {
                            var selectedIndex by remember { mutableIntStateOf(0) }
                            val selectedText: String = comboBoxItems[selectedIndex]

                            Text("Not editable + disabled")
                            Text(text = "Selected item: $selectedText")

                            //                            ListComboBox(
                            //                                items = comboBoxItems,
                            //                                selectedIndex = selectedIndex,
                            //                                onSelectedItemChange = { selectedIndex = it },
                            //                                modifier = Modifier.width(200.dp),
                            //                                enabled = false,
                            //                            )
                        }

                        Column {
                            var selectedIndex by remember { mutableIntStateOf(0) }
                            val selectedText: String = comboBoxItems[selectedIndex]

                            Text("Editable")
                            Text(text = "Selected item: $selectedText")

                            //                            EditableListComboBox(
                            //                                items = comboBoxItems,
                            //                                selectedIndex = selectedIndex,
                            //                                onSelectedItemChange = { selectedIndex = it },
                            //                                modifier = Modifier.width(200.dp),
                            //                                maxPopupHeight = 150.dp,
                            //                            )
                        }

                        Column {
                            var selectedIndex by remember { mutableIntStateOf(0) }
                            val selectedText: String = comboBoxItems[selectedIndex]

                            Text("Editable + disabled")
                            Text(text = "Selected item: $selectedText")

                            //                            EditableListComboBox(
                            //                                items = comboBoxItems,
                            //                                selectedIndex = selectedIndex,
                            //                                onSelectedItemChange = { selectedIndex = it },
                            //                                modifier = Modifier.width(200.dp),
                            //                                enabled = false,
                            //                            )
                        }
                    }
                }
            }
            .layout(RowLayout.PARENT_GRID)
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
            }
            .layout(RowLayout.PARENT_GRID)
    }

    private fun PaddingValues.vertical(): Dp = calculateTopPadding() + calculateBottomPadding()

    private fun PaddingValues.horizontal(layoutDirection: LayoutDirection): Dp =
        calculateStartPadding(layoutDirection) + calculateEndPadding(layoutDirection)

    private fun Row.compose(modifier: Modifier = Modifier.padding(8.dp), content: @Composable () -> Unit) =
        cell(JewelComposePanel { Box(modifier) { content() } }.apply { isOpaque = false })
}
