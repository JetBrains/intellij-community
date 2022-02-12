// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil

class JavaInheritorsCodeVisionProvider : JavaCodeVisionProviderBase() {
  companion object{
    const val ID = "java.inheritors"
  }

  override fun computeLenses(editor: Editor, psiFile: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()
    val traverser = SyntaxTraverser.psiTraverser(psiFile)
    for (element in traverser) {
      if (element is PsiClass) {
        val inheritors = JavaTelescope.collectInheritingClasses(element)
        if (inheritors == 0) continue
        val isInterface: Boolean = element.isInterface
        val hint = if (isInterface) JavaBundle.message("code.vision.implementations.hint", inheritors)
        else JavaBundle.message("code.vision.inheritors.hint", inheritors)
        lenses.add(
          Pair(element.textRange, ClickableTextCodeVisionEntry(hint, id, onClick = { mouseEvent, _ ->
            val navigationHandler = MarkerType.SUBCLASSED_CLASS.navigationHandler
            JavaCodeVisionUsageCollector.IMPLEMENTATION_CLICKED_EVENT_ID.log(element.getProject(),
                                                                             JavaCodeVisionUsageCollector.CLASS_LOCATION)
            navigationHandler.navigate(mouseEvent, element.nameIdentifier)
          })))
      }
      if (element is PsiMethod) {
        val overrides = JavaTelescope.collectOverridingMethods(element)
        if (overrides != 0) {
          val isAbstractMethod = isAbstractMethod(element)
          val hint = if (isAbstractMethod) JavaBundle.message("code.vision.implementations.hint", overrides)
          else JavaBundle.message("code.vision.overrides.hint", overrides)

          lenses.add(
            Pair(element.textRange, ClickableTextCodeVisionEntry(hint, id, onClick = { mouseEvent, e ->
              JavaCodeVisionUsageCollector.IMPLEMENTATION_CLICKED_EVENT_ID.log(element.getProject(),
                                                                               JavaCodeVisionUsageCollector.METHOD_LOCATION)
              val navigationHandler = MarkerType.OVERRIDDEN_METHOD.navigationHandler
              navigationHandler.navigate(mouseEvent, element.nameIdentifier)
            })))
        }
      }
    }
    return lenses
  }


  override val name: String
    get() = JavaBundle.message("settings.inlay.java.inheritors")
  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = emptyList()
  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Default
  override val id: String
    get() = ID
  override val groupId: String
    get() = PlatformCodeVisionIds.INHERITORS.key

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