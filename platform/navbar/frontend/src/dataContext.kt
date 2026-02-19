// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.Window
import javax.swing.JComponent
import kotlin.coroutines.resume

/**
 * This method assumes that [window] is an ancestor of [panel].
 *
 * @return data context of the focused component in the [window] of the [panel],
 * or `null` if [panel] has focus in hierarchy, or if the [window] of the [panel] is not focused
 */
internal suspend fun dataContext(window: Window, panel: JComponent): DataContext? = suspendCancellableCoroutine {
  IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(Runnable {
    it.resume(dataContextInner(window, panel))
  }, it.context.contextModality() ?: ModalityState.nonModal())
}

private fun dataContextInner(window: Window, panel: JComponent): DataContext? {
  if (!window.isFocused) {
    // IDEA-307406, IDEA-304798 Skip event when a window is out of focus (user is in a popup)
    return null
  }
  val focusedComponentInWindow = WindowManager.getInstance().getFocusedComponent(window)
                                 ?: return null
  if (UIUtil.isDescendingFrom(focusedComponentInWindow, panel)) {
    // ignore updates while panel or one of its children has focus
    return null
  }
  val dataContext = DataManager.getInstance().getDataContext(focusedComponentInWindow)
  val uiSnapshot = Utils.createAsyncDataContext(dataContext)
  return AnActionEvent.getInjectedDataContext(uiSnapshot)
}
