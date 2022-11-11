// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Ref
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.HintHint
import com.intellij.ui.LightweightHint
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.Consumer
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.KeyboardFocusManager
import javax.swing.JComponent
import javax.swing.JPanel


internal fun showHint(dataContext: DataContext, cs: CoroutineScope, project: Project, panel: NewNavBarPanel): LightweightHint {
  val component = JPanel(BorderLayout())
  component.add(panel)
  component.isOpaque = true
  if (ExperimentalUI.isNewUI()) {
    component.border = JBEmptyBorder(JBUI.CurrentTheme.StatusBar.Breadcrumbs.floatingBorderInsets())
    component.background = JBUI.CurrentTheme.StatusBar.Breadcrumbs.FLOATING_BACKGROUND
  }
  else {
    component.background = UIUtil.getListBackground()
  }
  val hint: LightweightHint = object : LightweightHint(component) {
    override fun hide() {
      super.hide()
      cs.cancel(null)
    }
  }
  hint.setForceShowAsPopup(true)
  hint.setFocusRequestor(panel)
  val editor = dataContext.getData(CommonDataKeys.EDITOR)
  if (editor == null) {
    val contextComponent = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)
    val relativePoint = getHintContainerShowPoint(project, panel, null, contextComponent)
    val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    val cmp = relativePoint.component
    if (cmp is JComponent && cmp.isShowing()) {
      hint.show(
        cmp, relativePoint.point.x, relativePoint.point.y,
        if (owner is JComponent) owner else null,
        HintHint(relativePoint.component, relativePoint.point)
      )
    }
  }
  else {
    val hintContainer = editor.contentComponent
    val rp = getHintContainerShowPoint(project, panel, hintContainer, null)
    val p = rp.getPointOn(hintContainer).point
    val hintInfo = HintHint(editor, p)
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, p, HintManager.HIDE_BY_ESCAPE, 0, true, hintInfo)
  }
  panel.onSizeChange = Runnable { hint.size = component.preferredSize }
  return hint
}


private fun getHintContainerShowPoint(
  project: Project,
  panel: JComponent,
  hintContainer: JComponent?,
  contextComponent: Component?
): RelativePoint {
  val myLocationCache = Ref<RelativePoint>()
  if (hintContainer != null) {
    val p = AbstractPopup.getCenterOf(hintContainer, panel)
    p.y -= hintContainer.visibleRect.height / 4
    myLocationCache.set(RelativePoint.fromScreen(p))
  }
  else {
    val dataManager = DataManager.getInstance()
    if (contextComponent != null) {
      val ctx = dataManager.getDataContext(contextComponent)
      myLocationCache.set(JBPopupFactory.getInstance().guessBestPopupLocation(ctx))
    }
    else {
      dataManager.dataContextFromFocus.doWhenDone(
        Consumer { dataContext: DataContext? ->
          val myContextComponent = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(
            dataContext!!)
          val ctx = dataManager.getDataContext(myContextComponent)
          myLocationCache.set(JBPopupFactory.getInstance().guessBestPopupLocation(ctx))
        })
    }
  }
  val c = myLocationCache.get().component
  if (!(c is JComponent && c.isShowing())) {
    //Yes. It happens sometimes.
    // 1. Empty frame. call nav bar, select some package and open it in Project View
    // 2. Call nav bar, then Esc
    // 3. Hide all tool windows (Ctrl+Shift+F12), so we've got empty frame again
    // 4. Call nav bar. NPE. ta da
    val ideFrame = WindowManager.getInstance().getIdeFrame(project)!!.component
    val rootPane = UIUtil.getRootPane(ideFrame)
    myLocationCache.set(JBPopupFactory.getInstance().guessBestPopupLocation(rootPane!!))
  }
  return myLocationCache.get()
}
