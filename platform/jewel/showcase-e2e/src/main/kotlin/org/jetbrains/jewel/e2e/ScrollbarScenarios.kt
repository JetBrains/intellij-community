// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.e2e

/**
 * Scrollbar and scroll indicator behaviours that must hold identically in the standalone sample and in the IDE.
 *
 * Exercises both the container-based scrollbars (`VerticallyScrollableContainer` / `HorizontallyScrollableContainer`)
 * and `Modifier.scrollIndicator` in the Scrollbars showcase view, including live transitions between scrollable and
 * non-scrollable content and runtime style switches.
 */
public object ScrollbarScenarios {
    private const val CONTAINER_APPROACH_DESCRIPTION = "Use Container approach"
    private const val MODIFIER_APPROACH_DESCRIPTION = "Use Modifier approach"

    private val containerPaneTags =
        listOf(
            ShowcaseE2eTags.SCROLLBARS_CONTAINER_LAZY_COLUMN,
            ShowcaseE2eTags.SCROLLBARS_CONTAINER_COLUMN,
            ShowcaseE2eTags.SCROLLBARS_CONTAINER_ALIGNED_COLUMN,
            ShowcaseE2eTags.SCROLLBARS_CONTAINER_ROW,
            ShowcaseE2eTags.SCROLLBARS_CONTAINER_LAZY_ROW,
            ShowcaseE2eTags.SCROLLBARS_CONTAINER_GRID_DEFAULT,
            ShowcaseE2eTags.SCROLLBARS_CONTAINER_GRID_SPAN_AWARE,
        )

    private val modifierPaneTags =
        listOf(
            ShowcaseE2eTags.SCROLLBARS_MODIFIER_LAZY_COLUMN,
            ShowcaseE2eTags.SCROLLBARS_MODIFIER_COLUMN,
            ShowcaseE2eTags.SCROLLBARS_MODIFIER_ALIGNED_COLUMN,
            ShowcaseE2eTags.SCROLLBARS_MODIFIER_ROW,
            ShowcaseE2eTags.SCROLLBARS_MODIFIER_LAZY_ROW,
        )

    /** Opens the scrollbars view and runs every scenario of this group, each isolated from the next. */
    public suspend fun runAll(automator: ShowcaseAutomator, failures: MutableList<String>) {
        automator.clickByContentDescription("Show Scrollbars")
        automator.waitForNode(ShowcaseE2eTags.SCROLLBARS_CONTAINER_LAZY_COLUMN)
        automator.runIsolated(
            failures,
            "The Container approach shows all scrollable container panes and grids",
            ::resetScrollbarsView,
            ::containerApproachShowsScrollablePanesAndGrids,
        )
        automator.runIsolated(
            failures,
            "Switching to the Modifier approach replaces containers with scroll indicators",
            ::resetScrollbarsView,
            ::modifierApproachSwitchesToScrollIndicators,
        )
        automator.runIsolated(
            failures,
            "Reduced content updates scrollable and non-scrollable states in both approaches",
            ::resetScrollbarsView,
            ::reducedContentUpdatesBothContainerAndModifierApproaches,
        )
        automator.runIsolated(
            failures,
            "Visibility and track click controls update Modifier scroll indicators at runtime",
            ::resetScrollbarsView,
            ::visibilityAndClickBehaviorControlsUpdateModifierPanes,
        )
    }

    /** Returns the Scrollbars view to the default Container approach with full content. */
    public suspend fun resetScrollbarsView(automator: ShowcaseAutomator) {
        if (!automator.isPresent(ShowcaseE2eTags.SCROLLBARS_CONTAINER_LAZY_COLUMN)) {
            automator.clickByContentDescription(CONTAINER_APPROACH_DESCRIPTION)
            automator.waitForNode(ShowcaseE2eTags.SCROLLBARS_CONTAINER_LAZY_COLUMN)
        }
        if (!automator.isPresent(ShowcaseE2eTags.SCROLLBARS_LAZY_COLUMN_FOURTH_ITEM)) {
            automator.click(ShowcaseE2eTags.SCROLLBARS_REDUCED_CONTENT)
            automator.waitForNode(ShowcaseE2eTags.SCROLLBARS_LAZY_COLUMN_FOURTH_ITEM)
        }
    }

    public suspend fun containerApproachShowsScrollablePanesAndGrids(automator: ShowcaseAutomator): String? {
        for (tag in containerPaneTags) {
            if (!automator.isPresent(tag)) {
                return "expected container pane '$tag' to be present in Container mode"
            }
        }
        for (tag in modifierPaneTags) {
            if (automator.isPresent(tag)) {
                return "unexpected modifier pane '$tag' present in Container mode"
            }
        }
        return null
    }

    public suspend fun modifierApproachSwitchesToScrollIndicators(automator: ShowcaseAutomator): String? {
        automator.clickByContentDescription(MODIFIER_APPROACH_DESCRIPTION)
        automator.waitForNode(ShowcaseE2eTags.SCROLLBARS_MODIFIER_LAZY_COLUMN)
        automator.waitForIdle()

        for (tag in modifierPaneTags) {
            if (!automator.isPresent(tag)) {
                return "expected modifier pane '$tag' to be present in Modifier mode"
            }
        }
        for (tag in containerPaneTags) {
            if (automator.isPresent(tag)) {
                return "unexpected container pane '$tag' still present in Modifier mode"
            }
        }

        // Scroll the Modifier LazyColumn down and back up to verify that scrolling works with Modifier.scrollIndicator.
        if (!automator.isPresent(ShowcaseE2eTags.SCROLLBARS_LAZY_COLUMN_FIRST_ITEM)) {
            return "expected first item to be present at rest in Modifier mode"
        }
        automator.scrollWheel(ShowcaseE2eTags.SCROLLBARS_MODIFIER_LAZY_COLUMN, wheelClicks = 15)
        automator.waitUntilGone(ShowcaseE2eTags.SCROLLBARS_LAZY_COLUMN_FIRST_ITEM)

        automator.scrollWheel(ShowcaseE2eTags.SCROLLBARS_MODIFIER_LAZY_COLUMN, wheelClicks = -15)
        automator.waitForNode(ShowcaseE2eTags.SCROLLBARS_LAZY_COLUMN_FIRST_ITEM)
        return null
    }

    public suspend fun reducedContentUpdatesBothContainerAndModifierApproaches(automator: ShowcaseAutomator): String? {
        if (!automator.isPresent(ShowcaseE2eTags.SCROLLBARS_LAZY_COLUMN_FOURTH_ITEM)) {
            return "expected fourth item to be present before enabling Reduced content"
        }

        // Reduce content in Container mode so panes become non-scrollable (3 items).
        automator.click(ShowcaseE2eTags.SCROLLBARS_REDUCED_CONTENT)
        automator.waitUntilGone(ShowcaseE2eTags.SCROLLBARS_LAZY_COLUMN_FOURTH_ITEM)
        if (!automator.isPresent(ShowcaseE2eTags.SCROLLBARS_LAZY_COLUMN_FIRST_ITEM)) {
            return "expected first item to remain present under Reduced content in Container mode"
        }

        // Switch to Modifier mode while Reduced content is active.
        automator.clickByContentDescription(MODIFIER_APPROACH_DESCRIPTION)
        automator.waitForNode(ShowcaseE2eTags.SCROLLBARS_MODIFIER_LAZY_COLUMN)
        if (!automator.isPresent(ShowcaseE2eTags.SCROLLBARS_LAZY_COLUMN_FIRST_ITEM)) {
            return "expected first item to be present under Reduced content in Modifier mode"
        }
        if (automator.isPresent(ShowcaseE2eTags.SCROLLBARS_LAZY_COLUMN_FOURTH_ITEM)) {
            return "unexpected fourth item present under Reduced content in Modifier mode"
        }

        // Restore full content in Modifier mode so panes transition live from non-scrollable to scrollable.
        automator.click(ShowcaseE2eTags.SCROLLBARS_REDUCED_CONTENT)
        automator.waitForNode(ShowcaseE2eTags.SCROLLBARS_LAZY_COLUMN_FOURTH_ITEM)
        return null
    }

    public suspend fun visibilityAndClickBehaviorControlsUpdateModifierPanes(automator: ShowcaseAutomator): String? {
        automator.clickByContentDescription(MODIFIER_APPROACH_DESCRIPTION)
        automator.waitForNode(ShowcaseE2eTags.SCROLLBARS_MODIFIER_LAZY_COLUMN)

        // Toggle Always visible twice to exercise both AlwaysVisible and WhenScrolling styles on all modifier panes.
        automator.click(ShowcaseE2eTags.SCROLLBARS_ALWAYS_VISIBLE)
        automator.waitForIdle()
        for (tag in modifierPaneTags) {
            if (!automator.isPresent(tag)) {
                return "modifier pane '$tag' disappeared after toggling Always visible"
            }
        }

        automator.click(ShowcaseE2eTags.SCROLLBARS_ALWAYS_VISIBLE)
        automator.waitForIdle()

        // Toggle track click behavior between JumpToSpot and NextPage.
        automator.click(ShowcaseE2eTags.SCROLLBARS_CLICK_JUMP_TO_SPOT)
        automator.waitForIdle()
        automator.click(ShowcaseE2eTags.SCROLLBARS_CLICK_NEXT_PAGE)
        automator.waitForIdle()

        for (tag in modifierPaneTags) {
            if (!automator.isPresent(tag)) {
                return "modifier pane '$tag' disappeared after toggling track click behavior"
            }
        }
        return null
    }
}
