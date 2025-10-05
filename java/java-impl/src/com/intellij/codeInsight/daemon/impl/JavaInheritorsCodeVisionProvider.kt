// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.InheritorsCodeVisionProvider
import com.intellij.java.JavaBundle
import com.intellij.java.analysis.OuterModelsModificationTrackerManager
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.pom.java.JavaFeature
import com.intellij.psi.*
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiUtil
import java.awt.event.MouseEvent

public class JavaInheritorsCodeVisionProvider : InheritorsCodeVisionProvider() {
  public companion object {
    public const val ID: String = "java.inheritors"
  }

  override fun acceptsFile(file: PsiFile): Boolean = file.language == JavaLanguage.INSTANCE

  override fun acceptsElement(element: PsiElement): Boolean =
    element is PsiClass && element !is PsiTypeParameter || element is PsiMethod

  override fun getVisionInfo(element: PsiElement, file: PsiFile): CodeVisionInfo? {
    if (element is PsiClass && element !is PsiTypeParameter) {
      val inheritors = computeClassInheritors(element, file.project)
      if (inheritors > 0) {
        val isInterface: Boolean = element.isInterface
        return CodeVisionInfo(if (isInterface) JavaBundle.message("code.vision.implementations.hint", inheritors)
        else JavaBundle.message("code.vision.inheritors.hint", inheritors), inheritors)
      }
    }
    else if (element is PsiMethod) {
      val overrides = computeMethodInheritors(element, file.project)
      if (overrides > 0) {
        val isAbstractMethod = isAbstractMethod(element)
        return CodeVisionInfo(if (isAbstractMethod) JavaBundle.message("code.vision.implementations.hint", overrides)
        else JavaBundle.message("code.vision.overrides.hint", overrides), overrides)
      }
    }
    return null
  }

  override fun getHint(element: PsiElement, file: PsiFile): String? {
    return getVisionInfo(element, file)?.text
  }

  private fun computeMethodInheritors(element: PsiMethod, project: Project) : Int {
    return CachedValuesManager.getCachedValue(element, CachedValueProvider {
      val overrides = JavaTelescope.collectOverridingMethods(element)
      CachedValueProvider.Result(overrides, OuterModelsModificationTrackerManager.getTracker(project))
    })
  }

  private fun computeClassInheritors(element: PsiClass, project: Project) : Int {
    return CachedValuesManager.getCachedValue(element, CachedValueProvider {
      val overrides = JavaTelescope.collectInheritingClasses(element)
      CachedValueProvider.Result(overrides, OuterModelsModificationTrackerManager.getTracker(project))
    })
  }

  override fun logClickToFUS(element: PsiElement, hint: String) {
    val location = if (element is PsiClass) JavaCodeVisionUsageCollector.CLASS_LOCATION else JavaCodeVisionUsageCollector.METHOD_LOCATION
    JavaCodeVisionUsageCollector.IMPLEMENTATION_CLICKED_EVENT_ID.log(element.project, location)
  }

  override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
    val markerType = if (element is PsiClass) MarkerType.SUBCLASSED_CLASS else MarkerType.OVERRIDDEN_METHOD
    val navigationHandler = markerType.navigationHandler
    if (element is PsiNameIdentifierOwner) {
      navigationHandler.navigate(event, element.nameIdentifier ?: element)
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
           PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, aClass)
  }
}