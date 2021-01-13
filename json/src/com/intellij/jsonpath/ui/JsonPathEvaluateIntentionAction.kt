// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.ui

import com.intellij.codeInsight.intention.AbstractIntentionAction
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.icons.AllIcons
import com.intellij.json.JsonBundle
import com.intellij.jsonpath.JsonPathLanguage
import com.intellij.jsonpath.ui.JsonPathEvaluateManager.Companion.JSON_PATH_EVALUATE_EXPRESSION_KEY
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import javax.swing.Icon

internal class JsonPathEvaluateIntentionAction : AbstractIntentionAction(), HighPriorityAction, Iconable {
  override fun getText(): String = JsonBundle.message("jsonpath.evaluate.intention")

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    if (file == null) return

    val manager = InjectedLanguageManager.getInstance(project)
    val jsonPath = if (manager.isInjectedFragment(file)) {
      manager.getUnescapedText(file)
    }
    else {
      file.text
    }

    JsonPathEvaluateManager.getInstance(project).evaluateExpression(jsonPath)
  }

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (editor == null || file == null) return false

    return file.language == JsonPathLanguage.INSTANCE
           && file.getUserData(JSON_PATH_EVALUATE_EXPRESSION_KEY) != true
  }

  override fun getIcon(flags: Int): Icon = AllIcons.FileTypes.Json
}