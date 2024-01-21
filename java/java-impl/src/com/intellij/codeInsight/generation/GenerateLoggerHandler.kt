// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.generation.ui.ChooseLoggerDialogWrapper
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.settings.JavaLoggerInfo
import com.intellij.settings.JavaSettingsStorage
import org.jetbrains.java.generate.GenerationUtil

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
    val field = factory.createFieldFromText(fieldText, lastClass).apply {
      PsiUtil.setModifierProperty(this, PsiModifier.STATIC, true)
      PsiUtil.setModifierProperty(this, PsiModifier.FINAL, true)
      PsiUtil.setModifierProperty(this, PsiModifier.PRIVATE, true)
    }

    try {
      val appendedField = insertLogger(project, field, lastClass, editor).singleOrNull()?.psiMember ?: return
      val identifier = appendedField.nameIdentifier
      editor.caretModel.moveToOffset(identifier.endOffset)
      editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }
    catch (e: Exception) {
      GenerationUtil.handleException(project, e)
    }
  }

  private fun insertLogger(project: Project,
                           field: PsiField,
                           clazz: PsiClass,
                           editor: Editor): List<PsiGenerationInfo<PsiField>> = WriteAction.compute<List<PsiGenerationInfo<PsiField>>, Exception> {
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(field)
    GenerateMembersUtil.insertMembersAtOffset(clazz, editor.caretModel.offset, listOf(PsiGenerationInfo(field)))
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
        val typeName = psiField.type.canonicalText

        for (logger in JavaLoggerInfo.allLoggers) {
          if (logger.loggerName == typeName) return false
        }
      }
      return psiClass.findFieldByName(JavaLoggerInfo.LOGGER_IDENTIFIER, false) == null
    }
  }
}