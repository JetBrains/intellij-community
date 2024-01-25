// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.generation.ui.ChooseLoggerDialogWrapper
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.logging.JvmLogger
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.parentsOfType
import com.intellij.refactoring.suggested.endOffset
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

    CommandProcessor.getInstance().executeCommand(project, {
      try {
        val appendedField = insertLogger(project, field, lastClass) ?: return@executeCommand
        val identifier = appendedField.nameIdentifier
        editor.caretModel.moveToOffset(identifier.endOffset)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
      }
      catch (e: Exception) {
        GenerationUtil.handleException(project, e)
      }
    }, null, null)
  }

  private fun insertLogger(project: Project,
                           field: PsiField,
                           clazz: PsiClass): PsiField? = WriteAction.compute<PsiField?, Exception> {
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(field)
    clazz.add(field) as? PsiField
  }

  private fun findSuitableLoggers(module: Module?): List<JvmLogger> = JvmLogger.EP_NAME.extensionList.filter {
    JavaLibraryUtil.hasLibraryClass(module, it.loggerName)
  }

  private fun getSelectedLogger(project: Project, availableLoggers: List<JvmLogger>): JvmLogger? {
    if (availableLoggers.isEmpty()) return null
    if (availableLoggers.size == 1) return availableLoggers.first()

    val preferredLogger = JvmLogger.getLoggerByName(project.service<JavaSettingsStorage>().state.logger)

    val chooseLoggerDialog = ChooseLoggerDialogWrapper(
      availableLoggers.map { it.toString() },
      (if (preferredLogger in availableLoggers) {
        preferredLogger
      }
      else {
        availableLoggers.first()
      }).toString(),
      project,
    )

    if (!chooseLoggerDialog.showAndGet()) return null

    return JvmLogger.getLoggerByName(chooseLoggerDialog.selectedLogger)
  }

  override fun startInWriteAction(): Boolean = false

  companion object {
    fun getPossiblePlacesForLogger(element: PsiElement): List<PsiClass> = element.parentsOfType(PsiClass::class.java, false)
      .filter { clazz -> clazz !is PsiAnonymousClass && isPossibleToPlaceLogger(clazz) }
      .toList()


    private fun isPossibleToPlaceLogger(psiClass: PsiClass): Boolean {
      for (psiField in psiClass.fields) {
        val typeName = psiField.type.canonicalText

        if (psiField.name == JvmLogger.LOGGER_IDENTIFIER) return false

        for (logger in JvmLogger.EP_NAME.extensionList) {
          if (logger.loggerName == typeName) return false
        }
      }
      return true
    }
  }
}