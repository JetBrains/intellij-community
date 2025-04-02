// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.footer

import com.intellij.find.impl.SearchEverywhereItem
import com.intellij.ide.actions.OpenInRightSplitAction
import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.lang.LangBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.ui.RelativeFont
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.util.concurrent.Callable
import java.util.function.Consumer
import javax.swing.JPanel

@NlsSafe
private val DEFAULT_TEXT = "<html><br></html>"

internal class ExtendedInfoComponent(private val project: Project?, private val advertisement: ExtendedInfo) {
  private val text = JBLabel()
    .apply {
      font = RelativeFont.NORMAL
        .scale(JBUI.CurrentTheme.Advertiser.FONT_SIZE_OFFSET.get())
        .derive(StartupUiUtil.labelFont)
      foreground = JBUI.CurrentTheme.Advertiser.foreground()
    }
  private var actionLink = ActionLink()
  private val shortcutLabel = JBLabel()

  @JvmField
  val component: JPanel =
    JPanel(BorderLayout(0, 0))
      .apply {
        isOpaque = true
        background = JBUI.CurrentTheme.Advertiser.background()
        border = JBUI.CurrentTheme.Advertiser.border()
      }

  init {
    component.add(text, BorderLayout.WEST)

    val actionPanel = JPanel(BorderLayout(2, 0))
    shortcutLabel.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    actionPanel.add(actionLink, BorderLayout.WEST)
    actionPanel.add(shortcutLabel, BorderLayout.EAST)
    actionPanel.background = JBUI.CurrentTheme.Advertiser.background()

    component.add(actionPanel, BorderLayout.EAST)
  }

  fun updateElement(element: Any, disposable: Disposable) {
    //preserve vertical space
    text.text = DEFAULT_TEXT
    actionLink.text = DEFAULT_TEXT

    val action = advertisement.rightAction.invoke(element)
    if (action != null) {
      val actionEvent = AnActionEvent.createEvent(action, context(project), null, ActionPlaces.ACTION_SEARCH, ActionUiKind.SEARCH_POPUP, null)
      action.updateIt(actionEvent)
      actionLink.update(actionEvent, action)
      shortcutLabel.updateIt(action)
    }

    ReadAction.nonBlocking(Callable<String> { advertisement.leftText.invoke(element) })
      .finishOnUiThread(ModalityState.nonModal(),
                        Consumer { leftText ->
                          if (leftText != null) {
                            text.text = StringUtil.shortenTextWithEllipsis(leftText, 80, 0)
                            text.toolTipText = leftText
                          }
                        })
      .expireWith(disposable)
      .submit(AppExecutorUtil.getAppExecutorService())
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

    private fun JBLabel.updateIt(action: AnAction) {
      text = KeymapUtil.getFirstKeyboardShortcutText(action)
    }
  }
}

internal class ExtendedInfoImpl(val contributors: List<SearchEverywhereContributor<*>>) : ExtendedInfo() {
  private val list = contributors.filterIsInstance<SearchEverywhereExtendedInfoProvider>().mapNotNull { it.createExtendedInfo() }

  init {
    leftText = fun(element: Any) = list.firstNotNullOfOrNull { it.leftText.invoke(element) }
    rightAction = fun(element: Any) = list.firstNotNullOfOrNull { it.rightAction.invoke(element) }
  }
}

internal fun createTextExtendedInfo(): ExtendedInfo {
  val psiElement: (Any) -> PsiElement? = { (it as? SearchEverywhereItem)?.usage?.element }
  val virtualFile: (Any) -> VirtualFile? = { (it as? SearchEverywhereItem)?.usage?.file }
  val project: (Any) -> Project? = { (it as? SearchEverywhereItem)?.usage?.usageInfo?.project }

  return createPsiExtendedInfo(project, virtualFile, psiElement)
}

@ApiStatus.Internal
fun createPsiExtendedInfo(): ExtendedInfo {
  val psiElement: (Any) -> PsiElement? = {
    val item = (it as? PsiItemWithSimilarity<*>)?.value ?: it
    (item as? PSIPresentationBgRendererWrapper.PsiItemWithPresentation)?.item
  }

  return createPsiExtendedInfo(project = null, file = null, psiElement)
}

fun createPsiExtendedInfo(project: ((Any) -> Project?)? = null,
                          file: ((Any) -> VirtualFile?)? = null,
                          psiElement: (Any) -> PsiElement?): ExtendedInfo {
  val projectFun = { item: Any -> project?.invoke(item) ?: psiElement.invoke(item)?.project }

  val fileFun = { item: Any ->
    file?.invoke(item) ?: psiElement.invoke(item)?.let {
      if (it is PsiFileSystemItem) {
        it.virtualFile
      }
      else {
        it.containingFile?.virtualFile
      }
    }
  }

  val path: (Any) -> String? = fun(item: Any): String? {
    val actualFile = fileFun.invoke(item)
    val actualProject = projectFun.invoke(item)
    if (actualFile == null) return null

    return ProjectFileIndex.getInstance(actualProject ?: return null).getSourceRootForFile(actualFile)
             ?.let { VfsUtilCore.getRelativePath(actualFile, it) }
           ?: FileUtil.getLocationRelativeToUserHome(actualFile.path)
  }

  val split: (Any) -> AnAction? = fun(item: Any): ExtendedInfoOpenInRightSplitAction? {
    return ExtendedInfoOpenInRightSplitAction(
      CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
        sink[CommonDataKeys.PROJECT] = projectFun.invoke(item)
        sink.lazy(CommonDataKeys.PSI_ELEMENT) {
          psiElement.invoke(item)
        }
        sink.lazy(CommonDataKeys.VIRTUAL_FILE) {
          fileFun.invoke(item)
        }
      })
  }

  return ExtendedInfo(path, split)
}

private class ExtendedInfoOpenInRightSplitAction(private val dataContext: DataContext) : AnAction() {
  val split = OpenInRightSplitAction()

  init {
    templatePresentation.text = LangBundle.message("search.everywhere.advertiser.class.on.in.split")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val event = AnActionEvent.createEvent(split, dataContext, null, ActionPlaces.ACTION_SEARCH, ActionUiKind.SEARCH_POPUP, null)
    ActionUtil.invokeAction(split, event, null)
    val seManager = SearchEverywhereManager.getInstance(dataContext.getData(CommonDataKeys.PROJECT))
    if (seManager.isShown) seManager.currentlyShownPopupInstance?.closePopup()
  }
}