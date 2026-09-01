// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase

/**
 * Test tags for showcase components, shared by the lanes that drive this UI: Spectre against the standalone sample, and
 * the remote driver against the IDE, where the same views are hosted by DevKit's components showcase dialog.
 *
 * Only tag what a test actually addresses. Toolbar navigation needs nothing here: the buttons already carry
 * `contentDescription = "Show <view title>"`, which both lanes can match on.
 */
internal object ShowcaseTestTags {
    /**
     * The string-based [org.jetbrains.jewel.ui.component.ListComboBox] in the Combo Boxes view.
     *
     * The end-to-end lanes repeat this literal in `ShowcaseE2eTags.COMBO_BOX`, because the module holding their shared
     * scenarios depends on neither host. Change both together.
     */
    const val STRING_LIST_COMBO_BOX: String = "showcase.comboBox.stringList"

    const val SCROLLBARS_ALWAYS_VISIBLE: String = "showcase.scrollbars.alwaysVisible"
    const val SCROLLBARS_REDUCED_CONTENT: String = "showcase.scrollbars.reducedContent"
    const val SCROLLBARS_CLICK_JUMP_TO_SPOT: String = "showcase.scrollbars.clickBehavior.jumpToSpot"
    const val SCROLLBARS_CLICK_NEXT_PAGE: String = "showcase.scrollbars.clickBehavior.nextPage"

    const val SCROLLBARS_CONTAINER_LAZY_COLUMN: String = "showcase.scrollbars.container.lazyColumn"
    const val SCROLLBARS_CONTAINER_COLUMN: String = "showcase.scrollbars.container.column"
    const val SCROLLBARS_CONTAINER_ALIGNED_COLUMN: String = "showcase.scrollbars.container.alignedColumn"
    const val SCROLLBARS_CONTAINER_ROW: String = "showcase.scrollbars.container.row"
    const val SCROLLBARS_CONTAINER_LAZY_ROW: String = "showcase.scrollbars.container.lazyRow"
    const val SCROLLBARS_CONTAINER_GRID_DEFAULT: String = "showcase.scrollbars.container.gridDefault"
    const val SCROLLBARS_CONTAINER_GRID_SPAN_AWARE: String = "showcase.scrollbars.container.gridSpanAware"

    const val SCROLLBARS_MODIFIER_LAZY_COLUMN: String = "showcase.scrollbars.modifier.lazyColumn"
    const val SCROLLBARS_MODIFIER_COLUMN: String = "showcase.scrollbars.modifier.column"
    const val SCROLLBARS_MODIFIER_ALIGNED_COLUMN: String = "showcase.scrollbars.modifier.alignedColumn"
    const val SCROLLBARS_MODIFIER_ROW: String = "showcase.scrollbars.modifier.row"
    const val SCROLLBARS_MODIFIER_LAZY_ROW: String = "showcase.scrollbars.modifier.lazyRow"

    const val SCROLLBARS_LAZY_COLUMN_FIRST_ITEM: String = "showcase.scrollbars.lazyColumn.firstItem"
    const val SCROLLBARS_LAZY_COLUMN_FOURTH_ITEM: String = "showcase.scrollbars.lazyColumn.fourthItem"
}
