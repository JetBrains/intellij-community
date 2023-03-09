// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.InheritorsCodeVisionProvider
import com.intellij.java.JavaBundle
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import java.awt.event.MouseEvent

class JavaInheritorsCodeVisionProvider : InheritorsCodeVisionProvider() {
  companion object {
    const val ID = "java.inheritors"
  }

  override fun acceptsFile(file: PsiFile): Boolean = file.language == JavaLanguage.INSTANCE

  override fun acceptsElement(element: PsiElement): Boolean =
    element is PsiClass && element !is PsiTypeParameter || element is PsiMethod

  override fun getHint(element: PsiElement, file: PsiFile): String? {
    if (element is PsiClass && element !is PsiTypeParameter) {
      val inheritors = JavaTelescope.collectInheritingClasses(element)
      if (inheritors > 0) {
        val isInterface: Boolean = element.isInterface
        return if (isInterface) JavaBundle.message("code.vision.implementations.hint", inheritors)
        else JavaBundle.message("code.vision.inheritors.hint", inheritors)
      }
    }
    else if (element is PsiMethod) {
      val overrides = JavaTelescope.collectOverridingMethods(element)
      if (overrides > 0) {
        val isAbstractMethod = isAbstractMethod(element)
        return if (isAbstractMethod) JavaBundle.message("code.vision.implementations.hint", overrides)
        else JavaBundle.message("code.vision.overrides.hint", overrides)
      }
    }
    return null
  }

  override fun logClickToFUS(element: PsiElement, hint: String) {
    val location = if (element is PsiClass) JavaCodeVisionUsageCollector.CLASS_LOCATION else JavaCodeVisionUsageCollector.METHOD_LOCATION
    JavaCodeVisionUsageCollector.IMPLEMENTATION_CLICKED_EVENT_ID.log(element.project, location)
  }

  override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
    val markerType = if (element is PsiClass) MarkerType.SUBCLASSED_CLASS else MarkerType.OVERRIDDEN_METHOD
    val navigationHandler = markerType.navigationHandler
    if (element is PsiNameIdentifierOwner) {
      navigationHandler.navigate(event, element.nameIdentifier)
    }
  }

  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = emptyList()
  override val id: String
    get() = ID

  private fun isAbstractMethod(method: PsiMethod): Boolean {
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return true
    }
    val aClass = method.containingClass
    return aClass != null && aClass.isInterface && !isDefaultMethod(aClass, method)
  }

  private fun isDefaultMethod(aClass: PsiClass, method: PsiMethod): Boolean {
    return method.hasModifierProperty(PsiModifier.DEFAULT) &&
           PsiUtil.getLanguageLevel(aClass).isAtLeast(LanguageLevel.JDK_1_8)
  }
}