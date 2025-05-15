// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.statusbar

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.*
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.annotations.ApiStatus
import javax.swing.JPanel

@ApiStatus.Internal
class CodeStyleStatusBarWidget(project: Project) : EditorBasedStatusBarPopup(project = project,
                                                                             isWriteableFileRequired = true), CodeStyleSettingsListener {
  private var panel: CodeStyleStatusBarPanel? = null

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    if (file == null) {
      return WidgetState.HIDDEN
    }

    val psiFile = getPsiFile() ?: return WidgetState.HIDDEN
    val settings = CodeStyle.getSettings(psiFile)
    val indentOptions = CodeStyle.getIndentOptions(psiFile)
    if (settings is TransientCodeStyleSettings) {
      val uiContributorFromModifier = getUiContributor(settings)
      if (uiContributorFromModifier != null) {
        return createWidgetState(psiFile = psiFile, indentOptions = indentOptions, uiContributor = uiContributorFromModifier)
      }
    }
    return createWidgetState(psiFile = psiFile, indentOptions = indentOptions, uiContributor = getUiContributor(file, indentOptions))
  }

  private fun getPsiFile(): PsiFile? {
    val editor = getEditor() ?: return null
    val project = project.takeIf { !it.isDisposed } ?: return null
    return PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
  }

  override fun createPopup(context: DataContext): ListPopup? {
    val state = getWidgetState(context.getData(CommonDataKeys.VIRTUAL_FILE))
    val editor = getEditor()
    val psiFile = getPsiFile()
    if (state is MyWidgetState && editor != null && psiFile != null) {
      val uiContributor = state.uiContributor
      val actions = ArrayList<AnAction>()
      actions.addAll(getActions(uiContributor, psiFile))
      for (modifier in CodeStyleSettingsModifier.EP_NAME.extensionList) {
        val activatingAction = modifier.getActivatingAction(uiContributor, psiFile)
        if (activatingAction != null) {
          actions.add(activatingAction);
        }
      }

      val actionGroup: ActionGroup = object : ActionGroup() {
        override fun getChildren(e: AnActionEvent?): Array<AnAction> = actions.toTypedArray()
      }

      return JBPopupFactory.getInstance().createActionGroupPopup(uiContributor?.actionGroupTitle,
                                                                 actionGroup,
                                                                 context,
                                                                 JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                                                 false)
    }
    return null
  }

  override fun registerCustomListeners(connection: MessageBusConnection) {
    connection.subscribe(CodeStyleSettingsListener.TOPIC, this)
  }

  override fun codeStyleSettingsChanged(event: CodeStyleSettingsChangeEvent) {
    update()
  }

  override fun createInstance(project: Project): StatusBarWidget = CodeStyleStatusBarWidget(project)

  override fun ID(): String = WIDGET_ID

  private class MyWidgetState(toolTip: @NlsContexts.Tooltip String?,
                              text: @NlsContexts.StatusBarText String?,
                              val uiContributor: CodeStyleStatusBarUIContributor?) : WidgetState(toolTip = toolTip,
                                                                                             text = text,
                                                                                             isActionEnabled = true) {
    init {
      if (uiContributor != null) {
        icon = uiContributor.icon
      }
    }
  }

  override fun createComponent(): JPanel {
    panel = CodeStyleStatusBarPanel()
    return panel!!
  }

  override fun updateComponent(state: WidgetState) {
    panel!!.setIcon(state.icon)
    panel!!.setText(state.text!!)
    panel!!.toolTipText = state.toolTip
  }

  override val isEmpty: Boolean
    get() = panel!!.text.isNullOrEmpty()

  companion object {
    const val WIDGET_ID: String = "CodeStyleStatusBarWidget"

    private fun getUiContributor(settings: TransientCodeStyleSettings): CodeStyleStatusBarUIContributor? {
      val modifier = settings.modifier
      return modifier?.getStatusBarUiContributor(settings)
    }

    private fun getUiContributor(file: VirtualFile,
                                 indentOptions: CommonCodeStyleSettings.IndentOptions): CodeStyleStatusBarUIContributor? {
      val provider = findProvider(file, indentOptions)
      return provider?.getIndentStatusBarUiContributor(indentOptions)
    }

    private fun findProvider(file: VirtualFile, indentOptions: CommonCodeStyleSettings.IndentOptions): FileIndentOptionsProvider? {
      val optionsProvider = indentOptions.fileIndentOptionsProvider
      if (optionsProvider != null) {
        return optionsProvider
      }

      for (provider in FileIndentOptionsProvider.EP_NAME.extensionList) {
        val uiContributor = provider.getIndentStatusBarUiContributor(indentOptions)
        if (uiContributor != null && uiContributor.areActionsAvailable(file)) {
          return provider
        }
      }
      return null
    }

    private fun createWidgetState(psiFile: PsiFile,
                                  indentOptions: CommonCodeStyleSettings.IndentOptions,
                                  uiContributor: CodeStyleStatusBarUIContributor?): WidgetState {
      return if (uiContributor != null) {
        MyWidgetState(toolTip = uiContributor.tooltip, text = uiContributor.getStatusText(psiFile), uiContributor = uiContributor)
      }
      else {
        val indentInfo = IndentStatusBarUIContributor.getIndentInfo(indentOptions)
        val tooltip = IndentStatusBarUIContributor.createTooltip(indentInfo, null)
        MyWidgetState(tooltip, indentInfo, null)
      }
    }

    private fun getActions(uiContributor: CodeStyleStatusBarUIContributor?, psiFile: PsiFile): Array<AnAction> {
      val allActions = ArrayList<AnAction>()
      if (uiContributor != null) {
        val actions = uiContributor.getActions(psiFile)
        if (actions != null) {
          allActions.addAll(actions)
        }
      }
      if (uiContributor == null || uiContributor is IndentStatusBarUIContributor && uiContributor.isShowFileIndentOptionsEnabled) {
        allActions.add(CodeStyleStatusBarWidgetFactory.createDefaultIndentConfigureAction(psiFile))
      }

      if (uiContributor != null) {
        val disabledAction = uiContributor.createDisableAction(psiFile.project)
        if (disabledAction != null) {
          allActions.add(disabledAction)
        }
        val showAllAction = uiContributor.createShowAllAction(psiFile.project)
        if (showAllAction != null) {
          allActions.add(showAllAction)
        }
      }
      return allActions.toArray(AnAction.EMPTY_ARRAY)
    }
  }
}