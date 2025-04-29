// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.ui.breakpoints.FieldBreakpoint
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl
import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiField
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase

class ToggleFieldBreakpointAction : AnAction(), ActionRemoteBehaviorSpecification.Disabled {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val place = getPlace(e) ?: return

    val document = place.getFile().getViewProvider().getDocument() ?: return
    val debuggerManager = DebuggerManagerEx.getInstanceEx(project)
    val manager = debuggerManager.getBreakpointManager()
    val offset = place.getOffset()
    val breakpoint = if (offset >= 0) manager.findBreakpoint(document, offset, FieldBreakpoint.CATEGORY) else null

    if (breakpoint == null) {
      val fieldBreakpoint = manager.addFieldBreakpoint(document, offset) ?: return
      val editor = e.getData(CommonDataKeys.EDITOR) ?: return
      manager.editBreakpoint(fieldBreakpoint, editor)
    }
    else {
      manager.removeBreakpoint(breakpoint)
    }
  }

  override fun update(event: AnActionEvent) {
    val place: SourcePosition? = getPlace(event)
    val toEnable = place != null

    val presentation = event.presentation
    if (ActionPlaces.PROJECT_VIEW_POPUP == event.place ||
        ActionPlaces.STRUCTURE_VIEW_POPUP == event.place ||
        ActionPlaces.BOOKMARKS_VIEW_POPUP == event.place
    ) {
      presentation.setVisible(toEnable)
    }
    presentation.setVisible(toEnable)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

private fun getPlace(event: AnActionEvent): SourcePosition? {
  val dataContext = event.dataContext
  val project = event.getData(CommonDataKeys.PROJECT) ?: return null
  if (ActionPlaces.PROJECT_VIEW_POPUP == event.place ||
      ActionPlaces.STRUCTURE_VIEW_POPUP == event.place ||
      ActionPlaces.BOOKMARKS_VIEW_POPUP == event.place
  ) {
    val psiElement = event.getData(CommonDataKeys.PSI_ELEMENT) as? PsiField ?: return null
    return SourcePosition.createFromElement(psiElement)
  }

  val value = XDebuggerTreeActionBase.getSelectedValue(dataContext)
  if (value is NodeDescriptorProvider) {
    val descriptor = (value as NodeDescriptorProvider).getDescriptor()
    if (descriptor is FieldDescriptorImpl) {
      val debuggerContext = DebuggerAction.getDebuggerContext(dataContext)
      val managerThread = debuggerContext.getManagerThread()
      if (managerThread != null) { // if there is an active debug session
        val sourcePosition = getSourcePositionNow(debuggerContext, descriptor)
        if (sourcePosition != null) {
          return sourcePosition
        }
      }
    }
  }

  val editor = ToggleMethodBreakpointAction.getEditor(event)
  if (editor != null) {
    val document = editor.getDocument()
    val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
    if (file != null) {
      val virtualFile = file.getVirtualFile()
      val fileType = virtualFile?.fileType
      if (JavaFileType.INSTANCE == fileType || JavaClassFileType.INSTANCE == fileType) {
        val field = FieldBreakpoint.findField(project, document, editor.getCaretModel().offset)
        if (field != null) {
          return SourcePosition.createFromElement(field)
        }
      }
    }
  }
  return null
}
