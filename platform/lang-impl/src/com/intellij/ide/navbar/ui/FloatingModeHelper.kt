// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.codeInsight.hint.HintManager.HIDE_BY_ESCAPE
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.HintHint
import com.intellij.ui.LightweightHint
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel


internal fun showHint(dataContext: DataContext, cs: CoroutineScope, project: Project, panel: NewNavBarPanel): LightweightHint {
  val wrappedPanel = wrapNavbarPanel(panel)
  val hint = createHint(cs, wrappedPanel)
  panel.onSizeChange = Runnable { hint.size = wrappedPanel.preferredSize }

  val editor = dataContext.getData(CommonDataKeys.EDITOR)
  if (editor != null) {
    showEditorHint(editor, project, hint)
  }
  else {
    showNonEditorHint(dataContext, project, hint)
  }

  return hint
}


private fun showEditorHint(editor: Editor, project: Project, hint: LightweightHint) {
  val hintContainer = editor.contentComponent
  val center = AbstractPopup.getCenterOf(hintContainer, hint.component)
  center.y -= hintContainer.visibleRect.height / 4
  val showPoint = RelativePoint.fromScreen(center).guessEvenBetterPopupLocation(project)
  val absoluteShowPoint = showPoint.getPointOn(hintContainer).point
  val hintInfo = HintHint(editor, absoluteShowPoint)
  HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, absoluteShowPoint, HIDE_BY_ESCAPE, 0, true, hintInfo)
}

private fun showNonEditorHint(dataContext: DataContext, project: Project, hint: LightweightHint) {
  val contextComponent = dataContext.getData(CONTEXT_COMPONENT)
                         ?: return
  val showPoint = JBPopupFactory.getInstance()
    .guessBestPopupLocation(dataContext)
    .guessEvenBetterPopupLocation(project)

  val component = showPoint.component
  if (component is JComponent && component.isShowing()) {
    val hintInfo = HintHint(showPoint.component, showPoint.point)
    hint.show(component, showPoint.point.x, showPoint.point.y, contextComponent as? JComponent, hintInfo)
  }
}

private fun wrapNavbarPanel(panel: NewNavBarPanel): JPanel =
  JPanel(BorderLayout()).apply {
    add(panel)
    isOpaque = true
    if (ExperimentalUI.isNewUI()) {
      border = JBEmptyBorder(JBUI.CurrentTheme.StatusBar.Breadcrumbs.floatingBorderInsets())
      background = JBUI.CurrentTheme.StatusBar.Breadcrumbs.FLOATING_BACKGROUND
    }
    else {
      background = UIUtil.getListBackground()
    }
  }

private fun createHint(cs: CoroutineScope, contents: JPanel): LightweightHint =
  object : LightweightHint(contents) {
    init {
      setForceShowAsPopup(true)
      setFocusRequestor(contents)
    }
    override fun hide() {
      super.hide()
      cs.cancel(null)
    }
  }

private fun RelativePoint.guessEvenBetterPopupLocation(project: Project): RelativePoint {
  if (component is JComponent && component.isShowing) {
    return this
  }

  //Yes. It happens sometimes.
  // 1. Empty frame. call nav bar, select some package and open it in Project View
  // 2. Call nav bar, then Esc
  // 3. Hide all tool windows (Ctrl+Shift+F12), so we've got empty frame again
  // 4. Call nav bar. NPE. ta da
  val ideFrame = WindowManager.getInstance().getIdeFrame(project)!!.component
  val rootPane = UIUtil.getRootPane(ideFrame)
  return JBPopupFactory.getInstance().guessBestPopupLocation(rootPane!!)
}
