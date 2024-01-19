// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.generation.ui.ChooseLoggerDialogWrapper
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.settings.JavaLoggerInfo
import com.intellij.settings.JavaSettingsStorage
import com.intellij.util.CommonJavaRefactoringUtil

class GenerateLoggerHandler : CodeInsightActionHandler {
  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val currentElement = file.findElementAt(editor.caretModel.offset) ?: return

    val places = getPossiblePlacesForLogger(currentElement)

    val lastClass = places.lastOrNull() ?: return
    val className = lastClass.name ?: return
    val factory = JavaPsiFacade.getElementFactory(project)

    val module = ModuleUtil.findModuleForFile(file)

    val availableLoggers = findSuitableLoggers(module)

    val chosenLogger = getSelectedLogger(project, availableLoggers) ?: return

    val fieldText = chosenLogger.createLoggerFieldText(className)
    val field = factory.createFieldFromText(fieldText, lastClass)
    val anchor = determineAnchor(lastClass)

    PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true)
    PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true)
    PsiUtil.setModifierProperty(field, PsiModifier.PRIVATE, true)


    ApplicationManager.getApplication().runWriteAction {
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(field)
      CommonJavaRefactoringUtil.appendField(lastClass, field, anchor, null)
    }
  }

  private fun findSuitableLoggers(module: Module?): List<JavaLoggerInfo> = JavaLoggerInfo.allLoggers.filter {
    JavaLibraryUtil.hasLibraryClass(module, it.loggerName)
  }

  private fun getSelectedLogger(project: Project, availableLoggers: List<JavaLoggerInfo>): JavaLoggerInfo? {
    if (availableLoggers.isEmpty()) return null
    if (availableLoggers.size == 1) return availableLoggers.first()

    val preferredLogger = project.service<JavaSettingsStorage>().state.logger

    val chooseLoggerDialog = ChooseLoggerDialogWrapper(
      availableLoggers,
      if (preferredLogger in availableLoggers) {
        preferredLogger
      }
      else {
        availableLoggers.first()
      },
      project,
    )

    if (!chooseLoggerDialog.showAndGet()) return null

    return chooseLoggerDialog.selectedLogger
  }

  override fun startInWriteAction(): Boolean = false

  private fun determineAnchor(psiClass: PsiClass): PsiElement? = psiClass.fields.firstOrNull()

  companion object {
    fun getPossiblePlacesForLogger(element: PsiElement): List<PsiClass> {
      val places = mutableListOf<PsiClass>()

      var psiClass: PsiClass? = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return places

      while (psiClass != null) {
        if (isPossibleToPlaceLogger(psiClass)) {
          places.add(psiClass)
        }
        psiClass = PsiTreeUtil.getParentOfType(psiClass, PsiClass::class.java)
      }
      return places
    }

    private fun isPossibleToPlaceLogger(psiClass: PsiClass): Boolean {
      for (psiField in psiClass.fields) {
        if (psiField.name == JavaLoggerInfo.LOGGER_IDENTIFIER) return false

        val typeName = psiField.type.canonicalText

        for (logger in JavaLoggerInfo.allLoggers) {
          if (logger.loggerName == typeName) return false
        }
      }
      return true
    }
  }
}