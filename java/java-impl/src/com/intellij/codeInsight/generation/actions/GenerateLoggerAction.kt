// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.actions

import com.intellij.codeInsight.generation.GenerateLoggerHandler
import com.intellij.lang.logging.JvmLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

public class GenerateLoggerAction : BaseGenerateAction(GenerateLoggerHandler()) {

  override fun isValidForFile(project: Project, editor: Editor, psiFile: PsiFile): Boolean {
    val element = psiFile.findElementAt(editor.caretModel.offset) ?: return false
    val module = ModuleUtil.findModuleForFile(psiFile)
    val availableLoggers = JvmLogger.findSuitableLoggers(module)
    return availableLoggers.isNotEmpty() && JvmLogger.getPossiblePlacesForLogger(element, availableLoggers).isNotEmpty()
  }
}