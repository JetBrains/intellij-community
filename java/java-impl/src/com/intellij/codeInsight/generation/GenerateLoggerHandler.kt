// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.generation.analysis.GenerateLoggerStatisticsCollector
import com.intellij.codeInsight.generation.ui.ChooseLoggerDialogWrapper
import com.intellij.java.JavaBundle
import com.intellij.lang.logging.JvmLogger
import com.intellij.lang.logging.UnspecifiedLogger
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.util.endOffset
import com.intellij.refactoring.IntroduceTargetChooser
import com.intellij.refactoring.introduce.PsiIntroduceTarget
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.logging.JvmLoggingSettingsStorage
import org.jetbrains.java.generate.GenerationUtil

public class GenerateLoggerHandler : CodeInsightActionHandler {
  override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
    GenerateLoggerStatisticsCollector.logActionInvoked(project)

    val currentElement = psiFile.findElementAt(editor.caretModel.offset) ?: return

    val module = ModuleUtil.findModuleForFile(psiFile)
    val availableLoggers = JvmLogger.findSuitableLoggers(module)

    val places = JvmLogger.getPossiblePlacesForLogger(currentElement, availableLoggers)
    val chosenLogger = getSelectedLogger(project, availableLoggers) ?: return

    when (places.size) {
      0 -> {
        CommonRefactoringUtil.showErrorHint(
          project,
          editor,
          JavaBundle.message("generate.logger.no.place.found.dialog.message"),
          JavaBundle.message("generate.logger.no.place.found.dialog.title"),
          null
        )
        return
      }
      1 -> execute(places.first(), chosenLogger, project, editor)
      else -> {
        val targetInfo = places.map { PsiTargetClassInfo(it) }
        IntroduceTargetChooser.showIntroduceTargetChooser(
          editor,
          targetInfo,
          {
            val clazz = it.place ?: return@showIntroduceTargetChooser
            execute(clazz, chosenLogger, project, editor)
          },
          JavaBundle.message("generate.logger.specify.place.popup.title"),
          0
        )
      }
    }
  }

  private fun execute(clazz: PsiClass,
                      chosenLogger: JvmLogger,
                      project: Project,
                      editor: Editor) {
    CommandProcessor.getInstance().executeCommand(project, {
      try {
        val insertedLogger = insertLoggerAtClass(chosenLogger, project, clazz) ?: return@executeCommand
        val offset = if (insertedLogger is PsiField) {
          insertedLogger.nameIdentifier.endOffset
        }
        else {
          insertedLogger.endOffset
        }
        editor.caretModel.moveToOffset(offset)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        GenerateLoggerStatisticsCollector.logActionCompleted(project)
      }
      catch (e: Exception) {
        GenerationUtil.handleException(project, e)
      }
    }, null, null)
  }

  private fun insertLoggerAtClass(logger: JvmLogger, project: Project, clazz: PsiClass): PsiElement? {
    val loggerText = logger.createLogger(project, clazz) ?: return null
    return WriteAction.compute<PsiElement?, Exception> { logger.insertLoggerAtClass(project, clazz, loggerText) }
  }

  private fun getSelectedLogger(project: Project, availableLoggers: List<JvmLogger>): JvmLogger? {
    val selectedLogger = when (availableLoggers.size) {
      0 -> null
      1 -> availableLoggers.first()
      else -> {
        val preferredLogger = JvmLogger.getLoggerById(project.service<JvmLoggingSettingsStorage>().state.loggerId)

        val selectedLogger = (if (preferredLogger in availableLoggers) preferredLogger else availableLoggers.first()) ?: return null

        val chooseLoggerDialog = ChooseLoggerDialogWrapper(
          project,
          availableLoggers,
          selectedLogger,
        )
        chooseLoggerDialog.show()
        if (chooseLoggerDialog.exitCode != DialogWrapper.OK_EXIT_CODE) return null

        chooseLoggerDialog.selectedLogger
      }
    }
    saveLoggerAfterFirstTime(project, selectedLogger)

    return selectedLogger
  }

  private fun saveLoggerAfterFirstTime(project: Project, logger: JvmLogger?) {
    if (logger == null) return
    val settings = project.service<JvmLoggingSettingsStorage>().state
    if (settings.loggerId == UnspecifiedLogger.UNSPECIFIED_LOGGER_ID) {
      settings.loggerId = logger.id
    }
  }

  override fun startInWriteAction(): Boolean = false
}

private class PsiTargetClassInfo(clazz: PsiClass) : PsiIntroduceTarget<PsiClass>(clazz) {
  private val className: String = clazz.name ?: throw IllegalStateException("Unable to fetch class name")

  override fun render(): String = "class $className"

  override fun getTextRange(): TextRange = place?.identifyingElement?.textRange ?: throw IllegalStateException(
    "Unable to fetch identifier of the class"
  )
}