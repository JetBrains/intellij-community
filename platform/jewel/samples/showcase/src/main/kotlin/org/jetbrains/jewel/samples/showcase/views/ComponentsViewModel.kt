// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.views

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.jetbrains.jewel.samples.showcase.components.Banners
import org.jetbrains.jewel.samples.showcase.components.Borders
import org.jetbrains.jewel.samples.showcase.components.Buttons
import org.jetbrains.jewel.samples.showcase.components.Checkboxes
import org.jetbrains.jewel.samples.showcase.components.ChipsAndTrees
import org.jetbrains.jewel.samples.showcase.components.ComboBoxes
import org.jetbrains.jewel.samples.showcase.components.Icons
import org.jetbrains.jewel.samples.showcase.components.Links
import org.jetbrains.jewel.samples.showcase.components.ProgressBar
import org.jetbrains.jewel.samples.showcase.components.RadioButtons
import org.jetbrains.jewel.samples.showcase.components.Scrollbars
import org.jetbrains.jewel.samples.showcase.components.SegmentedControls
import org.jetbrains.jewel.samples.showcase.components.ShowcaseIcons
import org.jetbrains.jewel.samples.showcase.components.Sliders
import org.jetbrains.jewel.samples.showcase.components.SplitLayouts
import org.jetbrains.jewel.samples.showcase.components.Tabs
import org.jetbrains.jewel.samples.showcase.components.TextAreas
import org.jetbrains.jewel.samples.showcase.components.TextFields
import org.jetbrains.jewel.samples.showcase.components.Tooltips
import org.jetbrains.jewel.ui.component.SplitLayoutState
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility

public class ComponentsViewModel(
    alwaysVisibleScrollbarVisibility: ScrollbarVisibility.AlwaysVisible,
    whenScrollingScrollbarVisibility: ScrollbarVisibility.WhenScrolling,
) {
    private var outerSplitState by mutableStateOf(SplitLayoutState(0.5f))
    private var verticalSplitState by mutableStateOf(SplitLayoutState(0.5f))
    private var innerSplitState by mutableStateOf(SplitLayoutState(0.5f))

    public fun getViews(): SnapshotStateList<ViewInfo> = views

    private val views: SnapshotStateList<ViewInfo> =
        mutableStateListOf(
            ViewInfo(title = "Buttons", iconKey = ShowcaseIcons.Components.button, content = { Buttons() }),
            ViewInfo(
                title = "Radio Buttons",
                iconKey = ShowcaseIcons.Components.radioButton,
                content = { RadioButtons() },
            ),
            ViewInfo(title = "Checkboxes", iconKey = ShowcaseIcons.Components.checkbox, content = { Checkboxes() }),
            ViewInfo(title = "Combo Boxes", iconKey = ShowcaseIcons.Components.comboBox, content = { ComboBoxes() }),
            ViewInfo(title = "Chips and trees", iconKey = ShowcaseIcons.Components.tree, content = { ChipsAndTrees() }),
            ViewInfo(
                title = "Progressbar",
                iconKey = ShowcaseIcons.Components.progressBar,
                content = { ProgressBar() },
            ),
            ViewInfo(title = "Icons", iconKey = ShowcaseIcons.Components.toolbar, content = { Icons() }),
            ViewInfo(title = "Links", iconKey = ShowcaseIcons.Components.links, content = { Links() }),
            ViewInfo(title = "Borders", iconKey = ShowcaseIcons.Components.borders, content = { Borders() }),
            ViewInfo(
                title = "Segmented Controls",
                iconKey = ShowcaseIcons.Components.segmentedControls,
                content = { SegmentedControls() },
            ),
            ViewInfo(title = "Sliders", iconKey = ShowcaseIcons.Components.slider, content = { Sliders() }),
            ViewInfo(title = "Tabs", iconKey = ShowcaseIcons.Components.tabs, content = { Tabs() }),
            ViewInfo(title = "Tooltips", iconKey = ShowcaseIcons.Components.tooltip, content = { Tooltips() }),
            ViewInfo(title = "TextAreas", iconKey = ShowcaseIcons.Components.textArea, content = { TextAreas() }),
            ViewInfo(title = "TextFields", iconKey = ShowcaseIcons.Components.textField, content = { TextFields() }),
            ViewInfo(
                title = "Scrollbars",
                iconKey = ShowcaseIcons.Components.scrollbar,
                content = {
                    Scrollbars(
                        alwaysVisibleScrollbarVisibility = alwaysVisibleScrollbarVisibility,
                        whenScrollingScrollbarVisibility = whenScrollingScrollbarVisibility,
                    )
                },
            ),
            ViewInfo(
                title = "SplitLayout",
                iconKey = ShowcaseIcons.Components.splitlayout,
                content = {
                    SplitLayouts(outerSplitState, verticalSplitState, innerSplitState) {
                        outerSplitState = SplitLayoutState(0.5f)
                        verticalSplitState = SplitLayoutState(0.5f)
                        innerSplitState = SplitLayoutState(0.5f)
                    }
                },
            ),
            ViewInfo(title = "Banners", iconKey = ShowcaseIcons.Components.banners, content = { Banners() }),
        )

    private var _currentView: ViewInfo by mutableStateOf(views.first())

    public fun getCurrentView(): ViewInfo = _currentView

    public fun setCurrentView(view: ViewInfo) {
        _currentView = view
    }
}
