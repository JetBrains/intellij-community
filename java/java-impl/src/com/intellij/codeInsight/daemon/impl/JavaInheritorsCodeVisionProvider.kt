// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import java.awt.event.MouseEvent

class JavaInheritorsCodeVisionProvider : JavaCodeVisionProviderBase() {
  companion object {
    const val ID = "java.inheritors"
  }

  override fun computeLenses(editor: Editor, psiFile: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    // we want to let this provider work only in tests dedicated for code vision, otherwise they harm performance
    if (ApplicationManager.getApplication().isUnitTestMode && !CodeVisionHost.isCodeLensTest(editor)) return emptyList()
    val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()
    val traverser = SyntaxTraverser.psiTraverser(psiFile)
    for (element in traverser) {
      if (element is PsiClass && element !is PsiTypeParameter) {
        if (!InlayHintsUtils.isFirstInLine(element)) continue
        val inheritors = JavaTelescope.collectInheritingClasses(element)
        if (inheritors == 0) continue
        val isInterface: Boolean = element.isInterface
        val hint = if (isInterface) JavaBundle.message("code.vision.implementations.hint", inheritors)
        else JavaBundle.message("code.vision.inheritors.hint", inheritors)
        val range = InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
        val handler = ClickHandler(element, JavaCodeVisionUsageCollector.CLASS_LOCATION, MarkerType.SUBCLASSED_CLASS)
        lenses.add(range to ClickableTextCodeVisionEntry(hint, id, onClick = handler))
      }
      else if (element is PsiMethod) {
        if (!InlayHintsUtils.isFirstInLine(element)) continue
        val overrides = JavaTelescope.collectOverridingMethods(element)
        if (overrides != 0) {
          val isAbstractMethod = isAbstractMethod(element)
          val hint = if (isAbstractMethod) JavaBundle.message("code.vision.implementations.hint", overrides)
          else JavaBundle.message("code.vision.overrides.hint", overrides)
          val range = InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
          val handler = ClickHandler(element, JavaCodeVisionUsageCollector.METHOD_LOCATION, MarkerType.OVERRIDDEN_METHOD)
          lenses.add(range to ClickableTextCodeVisionEntry(hint, id, onClick = handler))
        }
      }
    }
    return lenses
  }

  override fun getPlaceholderCollector(editor: Editor, psiFile: PsiFile?): CodeVisionPlaceholderCollector? {
    if (psiFile == null) return null
    if (psiFile !is PsiJavaFile) return null
    return object: BypassBasedPlaceholderCollector {
      override fun collectPlaceholders(element: PsiElement, editor: Editor): List<TextRange> {
        if (!(element is PsiClass && element is PsiTypeParameter) && element !is PsiMethod) {
          return emptyList()
        }
        val range = InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
        return listOf(range)
      }
    }
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

  private class ClickHandler(
    element: PsiNameIdentifierOwner,
    private val location: String,
    private val markerType: MarkerType,
  ) : (MouseEvent?, Editor) -> Unit {
    private val elementPointer = SmartPointerManager.createPointer(element)

    override fun invoke(event: MouseEvent?, editor: Editor) {
      event ?: return
      val element = elementPointer.element ?: return
      val navigationHandler = markerType.navigationHandler
      JavaCodeVisionUsageCollector.IMPLEMENTATION_CLICKED_EVENT_ID.log(element.project, location)
      navigationHandler.navigate(event, element.nameIdentifier)
    }
  }
}