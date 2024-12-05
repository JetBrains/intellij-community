package org.jetbrains.jewel.samples.standalone.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.jetbrains.jewel.samples.standalone.StandaloneSampleIcons
import org.jetbrains.jewel.samples.standalone.view.component.Banners
import org.jetbrains.jewel.samples.standalone.view.component.Borders
import org.jetbrains.jewel.samples.standalone.view.component.Buttons
import org.jetbrains.jewel.samples.standalone.view.component.Checkboxes
import org.jetbrains.jewel.samples.standalone.view.component.ChipsAndTrees
import org.jetbrains.jewel.samples.standalone.view.component.Dropdowns
import org.jetbrains.jewel.samples.standalone.view.component.Icons
import org.jetbrains.jewel.samples.standalone.view.component.Links
import org.jetbrains.jewel.samples.standalone.view.component.ProgressBar
import org.jetbrains.jewel.samples.standalone.view.component.RadioButtons
import org.jetbrains.jewel.samples.standalone.view.component.Scrollbars
import org.jetbrains.jewel.samples.standalone.view.component.SegmentedControls
import org.jetbrains.jewel.samples.standalone.view.component.Sliders
import org.jetbrains.jewel.samples.standalone.view.component.SplitLayouts
import org.jetbrains.jewel.samples.standalone.view.component.Tabs
import org.jetbrains.jewel.samples.standalone.view.component.TextAreas
import org.jetbrains.jewel.samples.standalone.view.component.TextFields
import org.jetbrains.jewel.samples.standalone.view.component.Tooltips
import org.jetbrains.jewel.ui.component.SplitLayoutState

internal object ComponentsViewModel {
    private var outerSplitState by mutableStateOf(SplitLayoutState(0.5f))
    private var verticalSplitState by mutableStateOf(SplitLayoutState(0.5f))
    private var innerSplitState by mutableStateOf(SplitLayoutState(0.5f))

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
            ViewInfo(title = "Links", iconKey = StandaloneSampleIcons.Components.links, content = { Links() }),
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
                content = { Scrollbars() },
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
