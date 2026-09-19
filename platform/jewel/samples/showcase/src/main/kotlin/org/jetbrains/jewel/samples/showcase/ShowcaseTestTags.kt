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
}
