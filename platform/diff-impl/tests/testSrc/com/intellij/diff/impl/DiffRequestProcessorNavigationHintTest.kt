// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl

import com.intellij.idea.TestFor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.UIUtil
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JPanel

/**
 * The "press again to go to the next/previous file" hint must not crash when the diff panel has no
 * root pane. A split-mode (Remote Development) backend marks the panel as showing without a real
 * window, so [UIUtil.isShowing] passes but `getRootPane()` is null. A headless test hits the same
 * state, because [UIUtil.isShowing] is always true in headless mode.
 */
@TestApplication
@TestFor(issues = ["IJPL-250059"])
internal class DiffRequestProcessorNavigationHintTest {

  @Test
  fun notifyMessageWithoutRootPaneDoesNotThrow() {
    val contentPanel = JPanel()
    contentPanel.setSize(200, 200)

    // The crash preconditions: the panel reports as showing but is not attached to a window.
    assertTrue(UIUtil.isShowing(contentPanel))
    assertNull(contentPanel.rootPane)

    // No current editor, so the hint targets the content panel directly. Before the fix this threw
    // an NPE from LightweightHint.show, because the content panel has no root pane.
    DiffRequestProcessor.notifyMessage(DataContext.EMPTY_CONTEXT, contentPanel, true)
    DiffRequestProcessor.notifyMessage(DataContext.EMPTY_CONTEXT, contentPanel, false)
  }
}
