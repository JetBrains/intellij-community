package org.jetbrains.jewel.samples.standalone.viewmodel

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.jetbrains.jewel.intui.standalone.styling.dark
import org.jetbrains.jewel.intui.standalone.styling.default
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.samples.showcase.components.Banners
import org.jetbrains.jewel.samples.showcase.components.Borders
import org.jetbrains.jewel.samples.showcase.components.Buttons
import org.jetbrains.jewel.samples.showcase.components.Checkboxes
import org.jetbrains.jewel.samples.showcase.components.ChipsAndTrees
import org.jetbrains.jewel.samples.showcase.components.Dropdowns
import org.jetbrains.jewel.samples.showcase.components.Icons
import org.jetbrains.jewel.samples.showcase.components.Links
import org.jetbrains.jewel.samples.showcase.components.ProgressBar
import org.jetbrains.jewel.samples.showcase.components.RadioButtons
import org.jetbrains.jewel.samples.showcase.components.Scrollbars
import org.jetbrains.jewel.samples.showcase.components.StandaloneSampleIcons
import org.jetbrains.jewel.samples.standalone.view.component.SegmentedControls
import org.jetbrains.jewel.samples.standalone.view.component.Sliders
import org.jetbrains.jewel.samples.standalone.view.component.SplitLayouts
import org.jetbrains.jewel.samples.standalone.view.component.Tabs
import org.jetbrains.jewel.samples.standalone.view.component.TextAreas
import org.jetbrains.jewel.samples.standalone.view.component.TextFields
import org.jetbrains.jewel.samples.standalone.view.component.Tooltips
import org.jetbrains.jewel.ui.component.SplitLayoutState
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.LinkUnderlineBehavior
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility

internal object ComponentsViewModel {
    private var outerSplitState by mutableStateOf(SplitLayoutState(0.5f))
    private var verticalSplitState by mutableStateOf(SplitLayoutState(0.5f))
    private var innerSplitState by mutableStateOf(SplitLayoutState(0.5f))

    @OptIn(ExperimentalLayoutApi::class)
    val views: SnapshotStateList<ViewInfo> =
        mutableStateListOf(
            ViewInfo(title = "Buttons", iconKey = StandaloneSampleIcons.Components.button, content = { Buttons() }),
            ViewInfo(
                title = "Radio Buttons",
                iconKey = StandaloneSampleIcons.Components.radioButton,
                content = { RadioButtons() },
            ),
            ViewInfo(
                title = "Checkboxes",
                iconKey = StandaloneSampleIcons.Components.checkbox,
                content = { Checkboxes() },
            ),
            ViewInfo(
                title = "Dropdowns",
                iconKey = StandaloneSampleIcons.Components.comboBox,
                content = { Dropdowns() },
            ),
            ViewInfo(
                title = "Chips and trees",
                iconKey = StandaloneSampleIcons.Components.tree,
                content = { ChipsAndTrees() },
            ),
            ViewInfo(
                title = "Progressbar",
                iconKey = StandaloneSampleIcons.Components.progressBar,
                content = { ProgressBar() },
            ),
            ViewInfo(title = "Icons", iconKey = StandaloneSampleIcons.Components.toolbar, content = { Icons() }),
            ViewInfo(
                title = "Links",
                iconKey = StandaloneSampleIcons.Components.links,
                content = {
                    val dark = LinkStyle.dark(underlineBehavior = LinkUnderlineBehavior.ShowAlways)
                    val light = LinkStyle.light(underlineBehavior = LinkUnderlineBehavior.ShowAlways)
                    Links(dark, light)
                },
            ),
            ViewInfo(title = "Borders", iconKey = StandaloneSampleIcons.Components.borders, content = { Borders() }),
            ViewInfo(
                title = "Segmented Controls",
                iconKey = StandaloneSampleIcons.Components.segmentedControls,
                content = { SegmentedControls() },
            ),
            ViewInfo(title = "Sliders", iconKey = StandaloneSampleIcons.Components.slider, content = { Sliders() }),
            ViewInfo(title = "Tabs", iconKey = StandaloneSampleIcons.Components.tabs, content = { Tabs() }),
            ViewInfo(title = "Tooltips", iconKey = StandaloneSampleIcons.Components.tooltip, content = { Tooltips() }),
            ViewInfo(
                title = "TextAreas",
                iconKey = StandaloneSampleIcons.Components.textArea,
                content = { TextAreas() },
            ),
            ViewInfo(
                title = "TextFields",
                iconKey = StandaloneSampleIcons.Components.textField,
                content = { TextFields() },
            ),
            ViewInfo(
                title = "Scrollbars",
                iconKey = StandaloneSampleIcons.Components.scrollbar,
                content = {
                    val dark = ScrollbarStyle.dark()
                    val light = ScrollbarStyle.light()
                    val alwaysVisibleScrollbarVisibility = ScrollbarVisibility.AlwaysVisible.default()
                    val whenScrollingScrollbarVisibility = ScrollbarVisibility.WhenScrolling.default()
                    Scrollbars(
                        dark = dark,
                        light = light,
                        alwaysVisibleScrollbarVisibility = alwaysVisibleScrollbarVisibility,
                        whenScrollingScrollbarVisibility = whenScrollingScrollbarVisibility,
                    )
                },
            ),
            ViewInfo(
                title = "SplitLayout",
                iconKey = StandaloneSampleIcons.Components.splitlayout,
                content = {
                    SplitLayouts(outerSplitState, verticalSplitState, innerSplitState) {
                        outerSplitState = SplitLayoutState(0.5f)
                        verticalSplitState = SplitLayoutState(0.5f)
                        innerSplitState = SplitLayoutState(0.5f)
                    }
                },
            ),
            ViewInfo(title = "Banners", iconKey = StandaloneSampleIcons.Components.banners, content = { Banners() }),
        )
    var currentView by mutableStateOf(views.first())
}
