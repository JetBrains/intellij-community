// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.siyeh.ig.psiutils.JavaLoggingUtils.*

class GenerateLoggerHandler : CodeInsightActionHandler {
  companion object {
    private val LOGGERS = listOf(
      LogInfo(SLF4J, SLF4J_FACTORY, "getLogger","%s.class"),
      LogInfo(COMMONS_LOGGING, COMMONS_LOGGING_FACTORY, "getLog", "%s.class"),
      LogInfo(LOG4J, LOG4J_FACTORY, "getLogger", "%s.class"),
      LogInfo(LOG4J2, LOG4J2_FACTORY, "getLogger", "%s.class"),
      LogInfo(JAVA_LOGGING, JAVA_LOGGING_FACTORY, "getLogger", "%s.class.getName()"),
    )

    private const val LOGGER_IDENTIFIER = "LOGGER"

    private fun isPossibleToPlaceLogger(psiClass: PsiClass) : Boolean {
      for (psiField in psiClass.fields) {
        if (psiField.name == LOGGER_IDENTIFIER) return false

        val typeName = psiField.type.canonicalText

        for (logger in LOGGERS) {
          if (logger.loggerName == typeName) return false
        }
      }
      return true
    }

    fun getPossiblePlacesForLogger(element : PsiElement) : List<PsiClass> {
      val places = mutableListOf<PsiClass>()

      var psiClass : PsiClass? = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return places

      while (psiClass != null) {
        if (isPossibleToPlaceLogger(psiClass)) {
          places.add(psiClass)
        }
        psiClass = PsiTreeUtil.getParentOfType(psiClass, PsiClass::class.java)

      }
      return places
    }
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val currentElement = file.findElementAt(editor.caretModel.offset) ?: return

    val places = getPossiblePlacesForLogger(currentElement)

    val lastClass = places.lastOrNull() ?: return
    val className = lastClass.name ?: return
    val factory = JavaPsiFacade.getElementFactory(project)



    val module = ModuleUtil.findModuleForFile(file)
    for (logger in LOGGERS) {
      if (JavaLibraryUtil.hasLibraryClass(module, logger.loggerName)) {
        val fieldText = logger.createLoggerFieldText(className)
        val field = factory.createFieldFromText(fieldText, lastClass)

        PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true)
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true)
        PsiUtil.setModifierProperty(field, PsiModifier.PRIVATE, true)

        JavaCodeStyleManager.getInstance(project).shortenClassReferences(field)
        lastClass.add(field)
        break
      }
    }
  }

  private class LogInfo(val loggerName : String, val factoryName : String, val methodName: String, val classNamePattern: String) {
    fun createLoggerFieldText(className : String) =
      "$loggerName $LOGGER_IDENTIFIER = ${factoryName}.$methodName(${String.format(classNamePattern, className)});"
  }
}