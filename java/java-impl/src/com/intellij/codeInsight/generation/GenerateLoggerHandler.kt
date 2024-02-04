// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.generation.ui.ChooseLoggerDialogWrapper
import com.intellij.java.JavaBundle
import com.intellij.lang.logging.JvmLogger
import com.intellij.lang.logging.UnspecifiedLogger
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.parentsOfType
import com.intellij.refactoring.IntroduceTargetChooser
import com.intellij.refactoring.introduce.PsiIntroduceTarget
import com.intellij.refactoring.suggested.endOffset
import com.intellij.ui.logging.JavaSettingsStorage
import org.jetbrains.java.generate.GenerationUtil

class GenerateLoggerHandler : CodeInsightActionHandler {
  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val currentElement = file.findElementAt(editor.caretModel.offset) ?: return

    val module = ModuleUtil.findModuleForFile(file)
    val availableLoggers = findSuitableLoggers(module)

    val places = getPossiblePlacesForLogger(currentElement, availableLoggers)
    val chosenLogger = getSelectedLogger(project, availableLoggers) ?: return

    when (places.size) {
      0 -> return
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
        val preferredLogger = JvmLogger.getLoggerByName(project.service<JavaSettingsStorage>().state.loggerName)

        val chooseLoggerDialog = ChooseLoggerDialogWrapper(
          project,
          availableLoggers.map { it.toString() },
          (if (preferredLogger in availableLoggers) {
            preferredLogger
          }
          else {
            availableLoggers.first()
          }).toString(),
        )
        chooseLoggerDialog.show()
        if (chooseLoggerDialog.exitCode != DialogWrapper.OK_EXIT_CODE) return null

        JvmLogger.getLoggerByName(chooseLoggerDialog.selectedLogger)
      }
    }
    saveLoggerAfterFirstTime(project, selectedLogger)

    return selectedLogger
  }

  private fun saveLoggerAfterFirstTime(project: Project, logger: JvmLogger?) {
    if (logger == null) return
    val settings = project.service<JavaSettingsStorage>().state
    if (settings.loggerName == UnspecifiedLogger.UNSPECIFIED_LOGGER_NAME) {
      settings.loggerName = logger.toString()
    }
  }

  override fun startInWriteAction(): Boolean = false

  companion object {
    fun findSuitableLoggers(module: Module?): List<JvmLogger> = JvmLogger.getAllLoggers(false).filter { it.isAvailable(module) }

    fun getPossiblePlacesForLogger(element: PsiElement, loggerList: List<JvmLogger>): List<PsiClass> = element.parentsOfType(
      PsiClass::class.java, true)
      .filter { clazz -> clazz !is PsiAnonymousClass && isPossibleToPlaceLogger(clazz, loggerList) }
      .toList()
      .reversed()

    private fun isPossibleToPlaceLogger(psiClass: PsiClass, loggerList: List<JvmLogger>): Boolean = loggerList.all {
      it.isPossibleToPlaceLoggerAtClass(psiClass)
    }
  }
}

private class PsiTargetClassInfo(clazz: PsiClass) : PsiIntroduceTarget<PsiClass>(clazz) {
  private val className : String = clazz.name ?: throw IllegalStateException("Unable to fetch class name")

  override fun render(): String = "class $className"

  override fun getTextRange(): TextRange = place?.identifyingElement?.textRange ?: throw IllegalStateException(
    "Unable to fetch identifier of the class"
  )
}