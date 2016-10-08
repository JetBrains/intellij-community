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
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.impl.hints.ParameterNameHintsConfigurable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

class ConfigureParameterHintsSettings : AnAction() {

  init {
    val presentation = templatePresentation
    presentation.text = "Configure"
    presentation.description = "Configure Parameter Name Hints"
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

    val offset = editor.caretModel.offset

    val element = file.findElementAt(offset)
    val callExpression = PsiTreeUtil.getParentOfType(element, PsiCallExpression::class.java)

    val result = callExpression?.resolveMethodGenerics()?.element ?: return
    if (result is PsiMethod) {
      val info = ParameterNameHintsManager.getMethodInfo(result)
      val pattern = info.fullyQualifiedName + '(' + info.paramNames.joinToString(",") + ')'
      ParameterNameHintsSettings.getInstance().addIgnorePattern(pattern)
    }
  }
  
}