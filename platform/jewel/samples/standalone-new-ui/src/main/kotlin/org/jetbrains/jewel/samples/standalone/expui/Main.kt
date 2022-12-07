package org.jetbrains.jewel.samples.standalone.expui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.themes.expui.desktop.window.JBWindow
import org.jetbrains.jewel.themes.expui.standalone.control.ActionButton
import org.jetbrains.jewel.themes.expui.standalone.control.CloseableTab
import org.jetbrains.jewel.themes.expui.standalone.control.ComboBox
import org.jetbrains.jewel.themes.expui.standalone.control.DropdownLink
import org.jetbrains.jewel.themes.expui.standalone.control.DropdownMenu
import org.jetbrains.jewel.themes.expui.standalone.control.DropdownMenuItem
import org.jetbrains.jewel.themes.expui.standalone.control.ExternalLink
import org.jetbrains.jewel.themes.expui.standalone.control.Icon
import org.jetbrains.jewel.themes.expui.standalone.control.Label
import org.jetbrains.jewel.themes.expui.standalone.control.Link
import org.jetbrains.jewel.themes.expui.standalone.control.OutlineButton
import org.jetbrains.jewel.themes.expui.standalone.control.PrimaryButton
import org.jetbrains.jewel.themes.expui.standalone.control.ProgressBar
import org.jetbrains.jewel.themes.expui.standalone.control.RadioButton
import org.jetbrains.jewel.themes.expui.standalone.control.SegmentedButton
import org.jetbrains.jewel.themes.expui.standalone.control.Tab
import org.jetbrains.jewel.themes.expui.standalone.control.TextArea
import org.jetbrains.jewel.themes.expui.standalone.control.TextField
import org.jetbrains.jewel.themes.expui.standalone.control.ToolBarActionButton
import org.jetbrains.jewel.themes.expui.standalone.control.Tooltip
import org.jetbrains.jewel.themes.expui.standalone.control.TriStateCheckbox
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalErrorAreaColors
import org.jetbrains.jewel.themes.expui.standalone.theme.DarkTheme
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme
import java.awt.Desktop
import java.net.URI
import kotlin.system.exitProcess

@OptIn(ExperimentalFoundationApi::class)
fun main() {
    application {
        var isDark by remember { mutableStateOf(false) }
        val theme = if (isDark) {
            DarkTheme
        } else {
            LightTheme
        }

        JBWindow(
            title = "Jewel New UI Sample",
            theme = theme,
            state = rememberWindowState(size = DpSize(900.dp, 700.dp)),
            onCloseRequest = {
                exitApplication()
                exitProcess(0)
            },
            mainToolBar = {
                Row(Modifier.mainToolBarItem(Alignment.End)) {
                    Tooltip("Open GitHub link in browser") {
                        ActionButton(
                            {
                                Desktop.getDesktop()
                                    .browse(URI.create("https://github.com/ButterCam/compose-jetbrains-theme"))
                            }, Modifier.size(40.dp), shape = RectangleShape
                        ) {
                            Icon("icons/github.svg")
                        }
                    }
                    Tooltip("Switch between dark and light mode,\ncurrently is ${if (isDark) "dark" else "light"} mode") {
                        ActionButton(
                            { isDark = !isDark }, Modifier.size(40.dp), shape = RectangleShape
                        ) {
                            if (isDark) {
                                Icon("icons/darkTheme.svg")
                            } else {
                                Icon("icons/lightTheme.svg")
                            }
                        }
                    }
                }
            }) {
            Row(
                Modifier.fillMaxSize()
            ) {
                Column(
                    Modifier.fillMaxHeight().width(40.dp).padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    var selected by remember { mutableStateOf(0) }
                    ToolBarActionButton(
                        selected == 0, { selected = 0 }, modifier = Modifier.size(30.dp)
                    ) {
                        Icon("icons/generic.svg", markerColor = LocalErrorAreaColors.current.text)
                    }
                    ToolBarActionButton(
                        selected == 1, { selected = 1 }, modifier = Modifier.size(30.dp)
                    ) {
                        Icon("icons/text.svg")
                    }
                }
                Spacer(Modifier.background(LocalAreaColors.current.startBorderColor).width(1.dp).fillMaxHeight())
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var state by remember { mutableStateOf(ToggleableState.Indeterminate) }
                        Tooltip("An action button") {
                            ActionButton({}, Modifier.size(30.dp)) {
                                Icon("icons/settings.svg")
                            }
                        }
                        Tooltip("An interactive tri-state checkbox") {
                            TriStateCheckbox(state, {
                                state = when (state) {
                                    ToggleableState.Off -> ToggleableState.On
                                    ToggleableState.On -> ToggleableState.Off
                                    ToggleableState.Indeterminate -> ToggleableState.On
                                }
                            })
                        }
                        Tooltip("A non-interactive tri-state checkbox, always is off") {
                            TriStateCheckbox(ToggleableState.Off, {}) {
                                Label("Off")
                            }
                        }

                        Tooltip("A non-interactive tri-state checkbox, always is on") {
                            TriStateCheckbox(ToggleableState.On, {}) {
                                Label("On")
                            }
                        }

                        Tooltip("A disabled tri-state checkbox, always is indeterminate") {
                            TriStateCheckbox(ToggleableState.Indeterminate, {}, enabled = false) {
                                Label("Indeterminate")
                            }
                        }

                        Tooltip("A disabled tri-state checkbox, always is off") {
                            TriStateCheckbox(ToggleableState.Off, {}, enabled = false) {
                                Label("Disabled")
                            }
                        }

                        Tooltip("A disabled tri-state checkbox, always is on") {
                            TriStateCheckbox(ToggleableState.On, {}, enabled = false) {
                                Label("Disabled")
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Tooltip("An outline style button") {
                            OutlineButton({}, modifier = Modifier.width(90.dp)) {
                                Label("Outline")
                            }
                        }

                        Tooltip("An disabled button") {
                            PrimaryButton({}, modifier = Modifier.width(90.dp), enabled = false) {
                                Label("Disabled")
                            }
                        }

                        Tooltip("A primary style button") {
                            PrimaryButton({}, modifier = Modifier.width(90.dp)) {
                                Label("Primary")
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Tooltip("Just some text") {
                            Label("Label")
                        }
                        Tooltip("A clickable text") {
                            Link("Link", {})
                        }
                        Tooltip("It can not be clicked anymore") {
                            Link("Disabled Link", {}, enabled = false)
                        }
                        Tooltip("Link to external website") {
                            ExternalLink("External Link", {})
                        }
                        Tooltip("Link that can open a dropdown list") {
                            var menuOpen by remember { mutableStateOf(false) }
                            DropdownLink("Dropdown Link", { menuOpen = true })
                            DropdownMenu(menuOpen, { menuOpen = false }) {
                                DropdownMenuItem({ menuOpen = false }) {
                                    Label("Item 1")
                                }
                                DropdownMenuItem({ menuOpen = false }) {
                                    Label("Item 2")
                                }
                                DropdownMenuItem({ menuOpen = false }) {
                                    Label("Item 3")
                                }
                            }
                        }
                    }

                    Tooltip("A segmented button, or radio button group") {
                        var selectedIndex by remember { mutableStateOf(0) }
                        val segmentedButtonItemCount = 3
                        SegmentedButton(segmentedButtonItemCount, selectedIndex, {
                            selectedIndex = it
                        }) {
                            when (it) {
                                0 -> Label("First")
                                1 -> Label("Second")
                                2 -> Label("Third")
                                else -> Label("Unknown")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var selectedIndex by remember { mutableStateOf(0) }
                        val firstRadioButtonId = 0
                        RadioButton(selectedIndex == firstRadioButtonId, {
                            selectedIndex = firstRadioButtonId
                        }) {
                            Label("First")
                        }
                        val secondRadioButtonId = 1
                        RadioButton(selectedIndex == secondRadioButtonId, {
                            selectedIndex = secondRadioButtonId
                        }, enabled = false) {
                            Label("Second")
                        }
                        val thirdRadioButtonId = 2
                        RadioButton(selectedIndex == thirdRadioButtonId, {
                            selectedIndex = thirdRadioButtonId
                        }) {
                            Label("Third")
                        }
                        val fourthRadioButtonId = 3
                        RadioButton(selectedIndex == thirdRadioButtonId, {
                            selectedIndex = thirdRadioButtonId
                        }) {
                            Label("Fourth")
                        }
                    }

                    TextField("TextField", {})

                    TextField("Rect", {}, shape = RectangleShape)

                    TextArea("This is a text area\nIt can be multiline", {})

                    val comboBoxItems = remember {
                        (0..100).map { "Item $it" }
                    }
                    var comboBoxSelection by remember {
                        mutableStateOf(comboBoxItems.first())
                    }

                    ComboBox(comboBoxItems, comboBoxSelection, {
                        comboBoxSelection = it
                    }, modifier = Modifier.width(150.dp), menuModifier = Modifier.width(150.dp))

                    InfiniteProgressBar()

                    ProgressBar()

                    Row(Modifier.height(40.dp).selectableGroup()) {
                        var selected by remember { mutableStateOf(0) }
                        Tab(selected == 0, {
                            selected = 0
                        }, modifier = Modifier.fillMaxHeight()) {
                            Label("First")
                        }
                        Tab(selected == 1, {
                            selected = 1
                        }, modifier = Modifier.fillMaxHeight()) {
                            Label("Second")
                        }
                        Tab(selected == 2, {
                            selected = 2
                        }, modifier = Modifier.fillMaxHeight()) {
                            Label("Third")
                        }
                    }

                    Row(Modifier.height(40.dp).selectableGroup()) {
                        var selected by remember { mutableStateOf(0) }
                        CloseableTab(selected == 0, {
                            selected = 0
                        }, {}, modifier = Modifier.fillMaxHeight()) {
                            Icon("icons/kotlin.svg")
                            Label("First.kt")
                        }
                        CloseableTab(selected == 1, {
                            selected = 1
                        }, {}, modifier = Modifier.fillMaxHeight()) {
                            Icon("icons/kotlin.svg")
                            Label("Second.kt")
                        }
                        CloseableTab(selected == 2, {
                            selected = 2
                        }, {}, modifier = Modifier.fillMaxHeight()) {
                            Icon("icons/kotlin.svg")
                            Label("Third.kt")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfiniteProgressBar() {
    val transition = rememberInfiniteTransition()
    val currentOffset by transition.animateFloat(0f, 1f, infiniteRepeatable(animation = keyframes {
        durationMillis = 1000
    }))

    ProgressBar(currentOffset)
}
