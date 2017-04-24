/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.inheritance

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.util.SmartList
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration

class ImplicitSubclassInspection : AbstractBaseUastLocalInspectionTool() {

  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {

    val classIsFinal = aClass.isFinal || aClass.hasModifierProperty(PsiModifier.PRIVATE)

    val problems = SmartList<ProblemDescriptor>()

    val subclassProviders = ImplicitSubclassProvider.EP_NAME.extensions
      .asSequence().filter { it.isApplicableTo(aClass) }

    val subclassInfos = subclassProviders.mapNotNull { it.getSubclassingInfo(aClass) }

    val methodsToOverride = aClass.methods.mapNotNull {
      method ->
      subclassInfos
        .mapNotNull { it.getOverridingInfo(method)?.description }
        .firstOrNull()?.let { description ->
        method to description
      }
    }

    val methodsToAttachToClassFix = if (classIsFinal)
      SmartList<SmartPsiElementPointer<UDeclaration>>()
    else null

    val smartPointerManager = SmartPointerManager.getInstance(aClass.project)

    for ((method, description) in methodsToOverride) {
      if (method.isFinal || method.isStatic || method.hasModifierProperty(PsiModifier.PRIVATE)) {
        methodsToAttachToClassFix?.add(smartPointerManager.createSmartPsiElementPointer(method, method.containingFile))

        val methodFixes = if (method.modifierList.isWritable)
          arrayOf<LocalQuickFix>(FixSubclassing(method, method.name))
        else
          emptyArray()

        problemTargets(method, methodHighlightableModifiersSet).forEach {
          problems.add(manager.createProblemDescriptor(
            it, description, isOnTheFly,
            methodFixes,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
        }
      }
    }

    if (classIsFinal) {
      val classReasonToBeSubclassed = subclassInfos.firstOrNull()?.description
      if ((methodsToOverride.isNotEmpty() || classReasonToBeSubclassed != null) && canApplyFix(aClass)) {
        problemTargets(aClass, classHighlightableModifiersSet).forEach {
          problems.add(manager.createProblemDescriptor(
            it, classReasonToBeSubclassed ?: InspectionsBundle.message("inspection.implicit.subclass.display.forClass", aClass.name),
            isOnTheFly,
            arrayOf<LocalQuickFix>(FixSubclassing(aClass, aClass.name ?: "class", methodsToAttachToClassFix ?: emptyList())),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          )
        }
      }
    }

    return problems.toTypedArray()
  }

  private fun canApplyFix(aClass: UClass) = aClass.modifierList?.isWritable ?: false

  private fun problemTargets(declaration: UDeclaration, highlightableModifiersSet: Set<String>): List<PsiElement> {
    val modifiersElements = declaration.modifierList?.let {
      it.getChildren().filter {
        it is PsiKeyword && highlightableModifiersSet.contains(it.getText())
      }
    } ?: emptyList<PsiElement>()

    if (modifiersElements.isEmpty())
      return (declaration as? PsiNameIdentifierOwner)?.nameIdentifier?.let { listOf(it) } ?: emptyList<PsiElement>()
    else
      return modifiersElements
  }

  private val methodHighlightableModifiersSet = setOf(PsiModifier.FINAL, PsiModifier.PRIVATE, PsiModifier.STATIC)

  private val classHighlightableModifiersSet = setOf(PsiModifier.FINAL, PsiModifier.PRIVATE)

  private class FixSubclassing(uDeclaration: UDeclaration,
                               hintTargetName: String,
                               val siblings: List<SmartPsiElementPointer<UDeclaration>> = emptyList())
    : LocalQuickFixOnPsiElement(uDeclaration) {

    override fun getFamilyName(): String = QuickFixBundle.message("fix.modifiers.family")

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
      makeExtendable(startElement as UDeclaration)
      for (sibling in siblings) {
        sibling.element?.let {
          makeExtendable(it)
        }
      }
    }

    private fun makeExtendable(declaration: UDeclaration) {
      val isClassMember = !(declaration is UClass)
      declaration.modifierList?.apply {
        setModifierProperty(PsiModifier.FINAL, false)
        setModifierProperty(PsiModifier.PRIVATE, false)
        if (isClassMember) {
          setModifierProperty(PsiModifier.STATIC, false)
        }
      }
      if (isClassMember) {
        (declaration.uastParent as? UClass)?.modifierList?.apply {
          setModifierProperty(PsiModifier.FINAL, false)
          setModifierProperty(PsiModifier.PRIVATE, false)
        }
      }
    }

    private val text = when (uDeclaration) {
      is UClass -> InspectionsBundle.message("inspection.implicit.subclass.make.class.extendable",
                                             hintTargetName,
                                             siblings.size,
                                             siblingsDescription())
      else -> InspectionsBundle.message("inspection.implicit.subclass.extendable", hintTargetName)
    }

    private fun siblingsDescription() =
      when (siblings.size) {
        1 -> "'${(siblings.firstOrNull()?.element as? PsiNamedElement)?.name}'"
        else -> ""
      }

    override fun getText(): String = text

  }

}