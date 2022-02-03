// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.codeInsight.hints.HintInfo.MethodInfo
import com.intellij.codeInsight.hints.settings.Diff
import com.intellij.codeInsight.hints.settings.InlayHintsConfigurable
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.codeInsight.hints.settings.language.ParameterInlayProviderSettingsModel
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.Language
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import java.util.function.Predicate


class ShowSettingsWithAddedPattern : AnAction(), UpdateInBackground {
  init {
    templatePresentation.description = CodeInsightBundle.message("inlay.hints.show.settings.description")
    templatePresentation.text = CodeInsightBundle.message("inlay.hints.show.settings", "_")
  }

  override fun update(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    
    val offset = editor.caretModel.offset
    val info = getHintInfoFromProvider(offset, file, editor)
    if (info is MethodInfo) {
      e.presentation.setText(info.getDisableHintText(), false)
    }
    else {
      e.presentation.isVisible = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    showParameterHintsDialog(e) {
      when (it) {
        is HintInfo.OptionInfo, null -> null
        is MethodInfo -> it.toPattern()
      }}
  }
}

class ShowParameterHintsSettings : AnAction(), UpdateInBackground {
  override fun actionPerformed(e: AnActionEvent) {
    showParameterHintsDialog(e) {null}
  }
}

fun showParameterHintsDialog(e: AnActionEvent, getPattern: (HintInfo?) -> String?) {
  val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
  val editor = e.getData(CommonDataKeys.EDITOR) ?: return

  val fileLanguage = file.language
  InlayParameterHintsExtension.forLanguage(fileLanguage) ?: return

  val offset = editor.caretModel.offset
  val info = getHintInfoFromProvider(offset, file, editor)

  val selectedLanguage = (info as? MethodInfo)?.language ?: fileLanguage

  when (val pattern = getPattern(info)) {
    null -> InlayHintsConfigurable.showSettingsDialogForLanguage(file.project, fileLanguage,
                                                                 Predicate { it is ParameterInlayProviderSettingsModel })
    else -> ExcludeListDialog(selectedLanguage, pattern).show()
  }
}

@Suppress("IntentionDescriptionNotFoundInspection")
class AddToExcludeListCurrentMethodIntention : IntentionAction, LowPriorityAction {
  override fun getText(): String = CodeInsightBundle.message("inlay.hints.exclude.list.method")
  override fun getFamilyName(): String = CodeInsightBundle.message("inlay.hints.intention.family.name")

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    val language = file.language
    val hintsProvider = InlayParameterHintsExtension.forLanguage(language) ?: return false
    return hintsProvider.isBlackListSupported 
           && hasEditorParameterHintAtOffset(editor, file) 
           && isMethodHintAtOffset(editor, file) 
  }

  private fun isMethodHintAtOffset(editor: Editor, file: PsiFile): Boolean {
    val offset = editor.caretModel.offset
    return getHintInfoFromProvider(offset, file, editor) is MethodInfo
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val offset = editor.caretModel.offset

    val info = getHintInfoFromProvider(offset, file, editor) as? MethodInfo ?: return
    val language = info.language ?: file.language

    ParameterNameHintsSettings.getInstance().addIgnorePattern(getLanguageForSettingKey(language), info.toPattern())
    refreshAllOpenEditors()
    showHint(project, language, info)
  }
  
  private fun showHint(project: Project, language: Language, info: MethodInfo) {
    val methodName = info.getMethodName()

    val listener = NotificationListener { notification, event ->
      when (event.description) {
        "settings" -> showSettings(language)
        "undo" -> undo(language, info)
      }
      notification.expire()
    }


    Notification("Parameter Name Hints",
                 CodeInsightBundle.message("notification.inlay.method.added.to.exclude.list", methodName),
                 CodeInsightBundle.message("notification.show.parameter.hints.settings.or.undo.label"),
                 NotificationType.INFORMATION)
      .setListener(listener)
      .notify(project)
  }
  
  private fun showSettings(language: Language) {
    ExcludeListDialog(language).show()
  }
  
  private fun undo(language: Language, info: MethodInfo) {
    val settings = ParameterNameHintsSettings.getInstance()
    val languageForSettings = getLanguageForSettingKey(language)

    val diff = settings.getExcludeListDiff(languageForSettings)
    val updated = diff.added.toMutableSet().apply {
      remove(info.toPattern())
    }
    
    settings.setExcludeListDiff(languageForSettings, Diff(updated, diff.removed))
    refreshAllOpenEditors()
  }

  override fun startInWriteAction(): Boolean = false
}


@Suppress("IntentionDescriptionNotFoundInspection")
class DisableCustomHintsOption: IntentionAction, LowPriorityAction {
  @IntentionName
  private var lastOptionName = ""
  
  override fun getText(): String = getIntentionText()

  @IntentionName
  private fun getIntentionText(): String {
    val optionPrefix = CodeInsightBundle.message("inlay.hints.disable.option.shortening_rule", 0)
    val resultPrefix = CodeInsightBundle.message("inlay.hints.disable.option.shortening_rule", 1)
    if (optionPrefix.isNotBlank() && resultPrefix.isNotBlank() && lastOptionName.startsWith(optionPrefix)) {
      return resultPrefix + lastOptionName.substring(optionPrefix.length)
    }
    return CodeInsightBundle.message("inlay.hints.disable.option", lastOptionName)
  }
  
  override fun getFamilyName(): String = CodeInsightBundle.message("inlay.hints.intention.family.name")

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    InlayParameterHintsExtension.forLanguage(file.language) ?: return false
    if (!hasEditorParameterHintAtOffset(editor, file)) return false
    
    val option = getOptionHintAtOffset(editor, file) ?: return false
    lastOptionName = option.optionName
    
    return true 
  }

  private fun getOptionHintAtOffset(editor: Editor, file: PsiFile): HintInfo.OptionInfo? {
    val offset = editor.caretModel.offset
    return getHintInfoFromProvider(offset, file, editor) as? HintInfo.OptionInfo
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val option = getOptionHintAtOffset(editor, file) ?: return
    option.disable()
    refreshAllOpenEditors()
  }

  override fun startInWriteAction(): Boolean = false
}

@Suppress("IntentionDescriptionNotFoundInspection")
class EnableCustomHintsOption: IntentionAction, HighPriorityAction {
  @IntentionName
  private var lastOptionName = ""
  
  override fun getText(): String {
    val optionPrefix = CodeInsightBundle.message("inlay.hints.enable.option.shortening_rule").replace('|', ' ')
    if (optionPrefix.isNotBlank() && lastOptionName.startsWith(optionPrefix)) {
      return lastOptionName
    }
    return CodeInsightBundle.message("inlay.hints.enable.option", lastOptionName)
  }
  
  override fun getFamilyName(): String = CodeInsightBundle.message("inlay.hints.intention.family.name")

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    val language = file.language
    if(!isParameterHintsEnabledForLanguage(language)) return false
    if (editor !is EditorImpl) return false
    
    InlayParameterHintsExtension.forLanguage(file.language) ?: return false

    val option = getDisabledOptionInfoAtCaretOffset(editor, file) ?: return false
    lastOptionName = option.optionName

    return true
  }

  private fun getDisabledOptionInfoAtCaretOffset(editor: Editor, file: PsiFile): HintInfo.OptionInfo? {
    val offset = editor.caretModel.offset

    val element = file.findElementAt(offset) ?: return null
    val provider = InlayParameterHintsExtension.forLanguage(file.language) ?: return null

    val target = PsiTreeUtil.findFirstParent(element) { it is PsiFile
                                                        || provider.hasDisabledOptionHintInfo(it, file) }
    if (target == null || target is PsiFile) return null
    return provider.getHintInfo(target, file) as? HintInfo.OptionInfo
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val option = getDisabledOptionInfoAtCaretOffset(editor, file) ?: return
    option.enable()
    refreshAllOpenEditors()
  }

  override fun startInWriteAction(): Boolean = false

}


private fun InlayParameterHintsProvider.hasDisabledOptionHintInfo(element: PsiElement, file: PsiFile): Boolean {
  val info = getHintInfo(element, file)
  return info is HintInfo.OptionInfo && !info.isOptionEnabled()
}


class ToggleInlineHintsAction : AnAction(), UpdateInBackground {

  override fun update(e: AnActionEvent) {
    if (!InlayParameterHintsExtension.hasAnyExtensions()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
    val isHintsShownNow = isParameterHintsEnabledForLanguage(file.language)
    e.presentation.text = if (isHintsShownNow)
      CodeInsightBundle.message("inlay.hints.disable.action.text")
    else
      CodeInsightBundle.message("inlay.hints.enable.action.text")
    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
    val language = file.language
    val before = isParameterHintsEnabledForLanguage(language)
    setShowParameterHintsForLanguage(!before, language)

    refreshAllOpenEditors()
  }
}


private fun hasEditorParameterHintAtOffset(editor: Editor, file: PsiFile): Boolean {
  if (editor is EditorWindow || editor is ImaginaryEditor) return false
  
  val offset = editor.caretModel.offset
  val elementToRight = file.findElementAt(offset)
  if (hasHints(elementToRight, offset, editor)) return true
  return offset > 0 && hasHints(file.findElementAt(offset - 1), offset, editor)
}

private fun hasHints(element: PsiElement?,
                     offset: Int,
                     editor: Editor): Boolean {
  val startOffset = element?.textRange?.startOffset ?: offset
  val endOffset = element?.textRange?.endOffset ?: offset
  
  return ParameterHintsPresentationManager.getInstance().getParameterHintsInRange(editor, startOffset, endOffset).isNotEmpty()
}


private fun refreshAllOpenEditors() {
  ParameterHintsPassFactory.forceHintsUpdateOnNextPass()
  ProjectManager.getInstance().openProjects.forEach { project ->
    val psiManager = PsiManager.getInstance(project)
    val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
    val fileEditorManager = FileEditorManager.getInstance(project)

    fileEditorManager.selectedFiles.forEach { file ->
      psiManager.findFile(file)?.let { daemonCodeAnalyzer.restart(it) }
    }
  }
}


private fun getHintInfoFromProvider(offset: Int, file: PsiFile, editor: Editor): HintInfo? {
  val element = file.findElementAt(offset)
  val infoForElementToRight = getInfoForElement(file, element, editor)
  if (infoForElementToRight != null) return infoForElementToRight
  if (offset == 0) return null
  return getInfoForElement(file, file.findElementAt(offset - 1), editor)
}

private fun getInfoForElement(file: PsiFile,
                              element: PsiElement?,
                              editor: Editor): HintInfo? {
  val provider = InlayParameterHintsExtension.forLanguage(file.language) ?: return null

  val method = PsiTreeUtil.findFirstParent(element) {
    it is PsiFile
    // hint owned by element
    || (provider.getHintInfo(it, file)?.isOwnedByPsiElement(it, editor) ?: false)
  }
  if (method == null || method is PsiFile) return null
  return provider.getHintInfo(method, file)
}

fun MethodInfo.toPattern(): String = this.fullyQualifiedName + '(' + this.paramNames.joinToString(",") + ')'
