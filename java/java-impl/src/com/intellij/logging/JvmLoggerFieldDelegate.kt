// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiUtil

class JvmLoggerFieldDelegate(
  private val factoryName: String,
  private val methodName: String,
  private val classNamePattern: String,
  override val loggerTypeName: String,
  override val priority: Int,
) : JvmLogger {
  override fun insertLoggerAtClass(project: Project, clazz: PsiClass, logger: PsiElement): PsiElement? {
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(logger)
    return clazz.add(logger)
  }

  override fun isAvailable(project: Project?): Boolean = JavaLibraryUtil.hasLibraryClass(project, loggerTypeName)

  override fun isAvailable(module: Module?): Boolean = JavaLibraryUtil.hasLibraryClass(module, loggerTypeName)

  override fun isPossibleToPlaceLoggerAtClass(clazz: PsiClass): Boolean = clazz
    .fields.any { it.name == LOGGER_IDENTIFIER || it.type.canonicalText == loggerTypeName }.not()

  override fun createLoggerElementText(project: Project, clazz: PsiClass): PsiField? {
    val factory = JavaPsiFacade.getElementFactory(project)
    val className = clazz.name ?: return null
    val fieldText = "$loggerTypeName $LOGGER_IDENTIFIER = ${factoryName}.$methodName(${
      String.format(classNamePattern, className)
    });"
    return factory.createFieldFromText(fieldText, clazz).apply {
      PsiUtil.setModifierProperty(this, PsiModifier.STATIC, true)
      PsiUtil.setModifierProperty(this, PsiModifier.FINAL, true)
      PsiUtil.setModifierProperty(this, PsiModifier.PRIVATE, true)
    }
  }

  companion object {
    private const val LOGGER_IDENTIFIER = "LOGGER"
  }
}