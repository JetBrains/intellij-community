// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.CompositeSelectInTarget
import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInManager
import com.intellij.ide.SelectInTarget
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal interface SelectInTargetPreferringEditorContext

internal fun createSelectInTargetAction(target: SelectInTarget, context: SelectInContext, editorContext: SelectInContext?): AnAction =
  if (target is CompositeSelectInTarget) {
    SelectInTargetActionGroup(SelectInTargetActionImpl(target, context, editorContext))
  } else {
    SelectInTargetAction(SelectInTargetActionImpl(target, context, editorContext))
  }

private class SelectInTargetActionGroup(
  private val impl: SelectInTargetActionImpl<CompositeSelectInTarget>,
) : ActionGroup(), DumbAware {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    impl.doUpdate(e)
    e.presentation.isPerformGroup = true
    e.presentation.isPopupGroup = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    impl.doPerform()
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> =
    impl.target.getSubTargets(impl.selectInContext)
      .sortedWith(SelectInManager.SelectInTargetComparator.INSTANCE)
      .map { createSelectInTargetAction(it, impl.selectInContext, impl.editorContext) }
      .toTypedArray()

}

private class SelectInTargetAction(
  private val impl: SelectInTargetActionImpl<*>
) : DumbAwareAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    impl.doUpdate(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    impl.doPerform()
  }

}

private class SelectInTargetActionImpl<T : SelectInTarget>(
  val target: T,
  val selectInContext: SelectInContext,
  var editorContext: SelectInContext?,
) {

  fun doUpdate(e: AnActionEvent) {
    e.updateSession.compute(this, "getText() and getIcon()", ActionUpdateThread.EDT) {
      e.presentation.text = getText()
      e.presentation.icon = getIcon()
    }
    e.presentation.isEnabled = isSelectable()
  }

  fun doPerform() {
    PsiDocumentManager.getInstance(selectInContext.project).commitAllDocuments()
    val context = if (editorContext != null && target is SelectInTargetPreferringEditorContext) editorContext else selectInContext
    target.selectIn(context, true)
  }

  @Nls
  private fun getText(): String {
    // toString() is annotated as @Nls, but the inspection doesn't recognize it
    @Suppress("HardCodedStringLiteral") var text: String = target.toString()
    val id: String? = if (target.minorViewId == null) target.toolWindowId else null
    val toolWindow = if (id == null) null else ToolWindowManager.getInstance(selectInContext.project).getToolWindow(id)
    val toolWindowId = target.toolWindowId
    if (toolWindow != null && toolWindowId != null) {
      //this code is left for compatibility with external plugins; plugins from intellij project return proper localized text from SelectInTarget::toString
      text = text.replace(toolWindowId, toolWindow.stripeTitle)
    }
    return text
  }

  private fun getIcon(): Icon? {
    val toolWindowManager = ToolWindowManager.getInstance(selectInContext.project)
    val id = if (target.minorViewId == null) target.toolWindowId else null
    val toolWindow = if (id == null) null else toolWindowManager.getToolWindow(id)
    return if (toolWindow != null) toolWindow.icon else EmptyIcon.ICON_13
  }

  private fun isSelectable(): Boolean {
    return if (DumbService.isDumb(selectInContext.project) && !DumbService.isDumbAware(target)) {
      false
    }
    else {
      target.canSelect(selectInContext)
    }
  }

}
