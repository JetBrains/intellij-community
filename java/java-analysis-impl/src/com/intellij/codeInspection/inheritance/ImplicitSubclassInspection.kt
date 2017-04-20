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

    val subclassers = ImplicitSubclassProvider.EP_NAME.extensions
      .asSequence()
      .filter { it.isApplicableTo(aClass) }

    val methodsToOverride = aClass.methods.mapNotNull {
      method ->
      subclassers
        .mapNotNull { it.findOverridingReason(method) }
        .firstOrNull()?.let { description ->
        method to description
      }
    }

    val classLevelFix =
      if (classIsFinal) {
        val classReasonToBeSubclassed = subclassers.mapNotNull { it.findSubclassingReason(aClass) }.firstOrNull()

        if (methodsToOverride.isNotEmpty() || classReasonToBeSubclassed != null) {
          val classLevelFix = FixSubclassing(aClass, aClass.name ?: "class")
          val classFixes = if (aClass.modifierList?.isWritable ?: false)
            arrayOf<LocalQuickFix>(classLevelFix)
          else emptyArray()

          problemTargets(aClass).forEach {
            problems.add(manager.createProblemDescriptor(
              it, classReasonToBeSubclassed ?: InspectionsBundle.message("inspection.implicit.subclass.display.forClass", aClass.name), isOnTheFly,
              classFixes,
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            )
          }

          classLevelFix
        }
        else null
      }
      else null

    for ((method, description) in methodsToOverride) {
      if (method.isFinal || method.isStatic || method.hasModifierProperty(PsiModifier.PRIVATE)) {

        classLevelFix?.siblings?.add(method)

        val methodFixes = if (method.modifierList.isWritable)
          arrayOf<LocalQuickFix>(FixSubclassing(method, method.name))
        else
          emptyArray()

        problemTargets(method).forEach {
          problems.add(manager.createProblemDescriptor(
            it, description, isOnTheFly,
            methodFixes,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
        }
      }
    }

    return problems.toTypedArray()
  }

  private fun problemTargets(declaration: UDeclaration): List<PsiElement> {

    val modifiersElements = declaration.modifierList?.let { highlightableModifiersElements(it) } ?: emptyList<PsiElement>()

    if (modifiersElements.isEmpty())
      return (declaration as? PsiNameIdentifierOwner)?.nameIdentifier?.let { listOf(it) } ?: emptyList<PsiElement>()
    else
      return modifiersElements
  }

  private val highlightableModifiersSet = setOf(PsiModifier.FINAL, PsiModifier.PRIVATE, PsiModifier.STATIC)

  private fun highlightableModifiersElements(memberModifierList: PsiModifierList): List<PsiElement> = memberModifierList.getChildren().filter {
    it is PsiKeyword && highlightableModifiersSet.contains(it.getText())
  }


  private class FixSubclassing(val uDeclaration: UDeclaration, val hintName: String) : LocalQuickFixOnPsiElement(uDeclaration) {

    val siblings = SmartList<UDeclaration>()

    override fun getFamilyName(): String = QuickFixBundle.message("fix.modifiers.family")

    override fun getText() = if (uDeclaration is UClass)
      InspectionsBundle.message("inspection.implicit.subclass.make.class.extendable")
    else
      InspectionsBundle.message("inspection.implicit.subclass.extendable", hintName)


    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
      makeExtendable(startElement as UDeclaration)
      for (sibling in siblings) {
        makeExtendable(sibling)
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


  }

}