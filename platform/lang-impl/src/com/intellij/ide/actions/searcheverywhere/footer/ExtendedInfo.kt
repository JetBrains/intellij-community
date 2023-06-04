// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.footer

import com.intellij.ide.actions.OpenInRightSplitAction
import com.intellij.ide.actions.searcheverywhere.ExtendedInfo
import com.intellij.ide.actions.searcheverywhere.ExtendedInfoComponent
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.ui.components.ActionLink
import com.intellij.util.concurrency.NonUrgentExecutor
import java.awt.BorderLayout
import java.util.concurrent.Callable

@NlsSafe
private val DEFAULT_TEXT = "<html><br></html>"

class ExtendedInfoComponentBase(val project: Project?, val advertisement: ExtendedInfo) : ExtendedInfoComponent() {
  private val text = createLabel()
  private var actionLink: ActionLink = ActionLink()

  init {
    myComponent.add(text, BorderLayout.WEST)
    myComponent.add(actionLink, BorderLayout.EAST)
  }

  fun updateElement(element: Any) {
    //preserve vertical space
    text.text = DEFAULT_TEXT
    actionLink.text = DEFAULT_TEXT

    val action = advertisement.rightAction.invoke(element)
    if (action != null) {
      val actionEvent = AnActionEvent.createFromAnAction(action, null, ActionPlaces.ACTION_SEARCH, context(project))
      action.updateIt(actionEvent)
      actionLink.update(actionEvent, action)
    }

    val leftText = advertisement.leftText.invoke(element)
    if (leftText != null) {
      text.text = StringUtil.shortenTextWithEllipsis(leftText, 80, 0)
      text.toolTipText = leftText
    }
  }

  companion object {
    private fun context(project: Project?) = project?.let { SimpleDataContext.getProjectContext(it) } ?: SimpleDataContext.EMPTY_CONTEXT
    private fun AnAction.updateIt(event: AnActionEvent) {
      let {
        ActionUtil.performDumbAwareUpdate(it, event, false)
      }
    }

    private fun ActionLink.update(event: AnActionEvent, action: AnAction) {
      text = event.presentation.text ?: DEFAULT_TEXT
      toolTipText = event.presentation.description
      actionListeners.forEach { removeActionListener(it) }
      addActionListener { _ -> ActionUtil.performActionDumbAwareWithCallbacks(action, event) }
    }
  }
}

fun createPsiExtendedInfo(): ExtendedInfo {
  val path: (Any) -> String? = fun(it: Any): String? {
    return (it as? PSIPresentationBgRendererWrapper.PsiItemWithPresentation)?.let {
      ReadAction.nonBlocking(Callable {
        runBlockingCancellable {
          val psiElement = it.item
          val file = if (psiElement is PsiFileSystemItem) {
            psiElement.virtualFile
          } else {
            psiElement.containingFile?.virtualFile
          } ?: return@runBlockingCancellable null

          val sourceRootForFile = readAction {
            ProjectFileIndex.getInstance(psiElement.project).getSourceRootForFile(file)
          }
          return@runBlockingCancellable VfsUtilCore.getRelativePath(file, sourceRootForFile ?: return@runBlockingCancellable null)
        }
      }).submit(NonUrgentExecutor.getInstance()).get()
    }
  }

  val shortcut: (Any) -> AnAction? = fun(it: Any?) =
    (it as? PSIPresentationBgRendererWrapper.PsiItemWithPresentation)?.let { ExtendedInfoOpenInRightSplitAction(it.item) }

  return ExtendedInfo(path, shortcut)
}

class ExtendedInfoOpenInRightSplitAction(val psiElement: PsiElement) : AnAction() {
  val split = OpenInRightSplitAction()

  init {
    templatePresentation.text = LangBundle.message("search.everywhere.advertiser.class.on.in.split")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val file = if (psiElement is PsiFileSystemItem) {
      psiElement.virtualFile
    }
    else {
      psiElement.containingFile?.virtualFile
    }

    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, e.project)
      .add(CommonDataKeys.PSI_ELEMENT, psiElement)
      .add(CommonDataKeys.VIRTUAL_FILE, file).build()

    ActionUtil.invokeAction(split, dataContext, ActionPlaces.ACTION_SEARCH, null, null)
  }
}