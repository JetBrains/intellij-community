// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.navbar.ui.NavBarItemComponent.Companion.isItemComponentFocusable
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.UIUtil
import java.awt.event.FocusEvent
import java.awt.event.FocusListener

/**
 * Consider the following steps:
 * - navigation bar is focused;
 * - user invokes Delete action;
 * - Delete dialog is open;
 * - user completes the dialog with OK;
 * - dialog is closed, element is removed;
 * - focus goes back to navigation bar.
 * Since the navigation bar is focused, no updates from focus data context are applied.
 * This listener detects the above situation, and moves the focus back to the editor.
 *
 * Original issue: https://youtrack.jetbrains.com/issue/IDEA-96105.
 */
internal class NavBarDialogFocusListener(private val panel: NewNavBarPanel) : FocusListener {

  private var shouldFocusEditor = false

  override fun focusGained(e: FocusEvent) {
    // If focus comes from anything in the nav bar panel, ignore the event
    if (isItemComponentFocusable() && UIUtil.isAncestor(panel, e.oppositeComponent)) {
      return
    }

    if (e.oppositeComponent == null) {
      if (shouldFocusEditor) {
        shouldFocusEditor = false
        ToolWindowManager.getInstance(panel.project).activateEditorComponent()
        return
      }
    }
  }

  override fun focusLost(e: FocusEvent) {
    // If focus reaches anything in nav bar panel, ignore the event
    if (isItemComponentFocusable() && UIUtil.isAncestor(panel, e.oppositeComponent)) {
      return
    }

    val dialog = DialogWrapper.findInstance(e.oppositeComponent)
    if (dialog != null) {
      if (dialog.isDisposed) {
        shouldFocusEditor = dialog.exitCode != DialogWrapper.CANCEL_EXIT_CODE
      }
      else {
        shouldFocusEditor = true
        Disposer.register(dialog.disposable, Disposable {
          if (dialog.exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
            shouldFocusEditor = false
          }
        })
      }
    }
  }
}
