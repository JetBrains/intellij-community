/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.codeInsight.hints.settings.ParameterNameHintsConfigurable
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil

class ShowParameterHintsSettings : AnAction() {
  init {
    val presentation = templatePresentation
    presentation.text = "Show Settings"
    presentation.description = "Show Parameter Name Hints Settings"
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = CommonDataKeys.PROJECT.getData(e.dataContext) ?: return
    val dialog = ParameterNameHintsConfigurable(project)
    dialog.show()
  }
}

class BlacklistCurrentMethodAction : AnAction() {
  init {
    val presentation = templatePresentation
    presentation.text = "Do Not Show Hints For Current Method"
    presentation.description = "Adds Current Method to Parameter Name Hints Blacklist"
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = CommonDataKeys.EDITOR.getData(e.dataContext) ?: return
    val file = CommonDataKeys.PSI_FILE.getData(e.dataContext) ?: return
    
    addMethodAtCaretToBlackList(editor, file)
  }
}

class BlacklistCurrentMethodIntention : IntentionAction, HighPriorityAction {
  override fun getText() = "Do not show hints for current method"
  override fun getFamilyName() = "Parameter Name Hints"

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    return InlayParameterHintsExtension.hasAnyExtensions() && hasParameterHintAtOffset(editor)
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    addMethodAtCaretToBlackList(editor, file)
  }

  override fun startInWriteAction() = false
}

class ToggleInlineHintsAction : AnAction() {
  
  companion object {
    private val disableText = "Disable Parameter Name Hints"
    private val enableText = "Enable Parameter Name Hints"
  }
  
  override fun update(e: AnActionEvent) {
    if (InlayParameterHintsExtension.hasAnyExtensions()) {
      e.presentation.isEnabledAndVisible = true
      val isShow = EditorSettingsExternalizable.getInstance().isShowParameterNameHints
      e.presentation.text = if (isShow) disableText else enableText
    }
    else {
      e.presentation.isEnabledAndVisible = false      
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val settings = EditorSettingsExternalizable.getInstance()
    val before = settings.isShowParameterNameHints
    settings.isShowParameterNameHints = !before

    refreshAllOpenEditors()
  }
}

private fun hasParameterHintAtOffset(editor: Editor): Boolean {
  val offset = editor.caretModel.offset
  if (editor.inlayModel.hasInlineElementAt(offset)) {
    val manager = ParameterHintsPresentationManager.getInstance()
    return editor.inlayModel
        .getInlineElementsInRange(offset, offset)
        .find { manager.isParameterHint(it) } != null
  }

  return false
}

private fun refreshAllOpenEditors() {
  ProjectManager.getInstance().openProjects.forEach {
    val psiManager = PsiManager.getInstance(it)
    val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(it)
    val fileEditorManager = FileEditorManager.getInstance(it)

    fileEditorManager.selectedFiles.forEach {
      psiManager.findFile(it)?.let { daemonCodeAnalyzer.restart(it) }
    }
  }
}

private fun addMethodAtCaretToBlackList(editor: Editor, file: PsiFile) {
  val offset = editor.caretModel.offset

  val element = file.findElementAt(offset)
  val hintsProvider = InlayParameterHintsExtension.forLanguage(file.language) ?: return

  val method = PsiTreeUtil.findFirstParent(element, { e -> hintsProvider.getMethodInfo(e) != null }) ?: return
  val info = hintsProvider.getMethodInfo(method) ?: return

  val pattern = info.fullyQualifiedName + '(' + info.paramNames.joinToString(",") + ')'
  ParameterNameHintsSettings.getInstance().addIgnorePattern(pattern)

  refreshAllOpenEditors()
}