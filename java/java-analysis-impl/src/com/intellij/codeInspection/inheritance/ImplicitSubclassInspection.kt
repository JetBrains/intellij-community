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

import com.intellij.CommonBundle
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.*
import com.intellij.java.analysis.JavaAnalysisBundle
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.lang.jvm.JvmNamedElement
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import com.intellij.uast.UastSmartPointer
import com.intellij.uast.createUastSmartPointer
import com.intellij.util.IncorrectOperationException
import com.intellij.util.SmartList
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.toUElementOfType

class ImplicitSubclassInspection : LocalInspectionTool() {

  private fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {

    val psiClass = aClass.javaPsi
    val classIsFinal = aClass.isFinal || psiClass.hasModifierProperty(PsiModifier.PRIVATE)

    val problems = SmartList<ProblemDescriptor>()

    val subclassProviders = ImplicitSubclassProvider.EP_NAME.extensions
      .asSequence().filter { it.isApplicableTo(psiClass) }

    val subclassInfos = subclassProviders.mapNotNull { it.getSubclassingInfo(psiClass) }

    val methodsToOverride = aClass.methods.mapNotNull { method ->
      subclassInfos
        .mapNotNull { it.methodsInfo?.get(method.javaPsi)?.description }
        .firstOrNull()?.let { description ->
        method to description
      }
    }

    val methodsToAttachToClassFix = if (classIsFinal)
      SmartList<UastSmartPointer<UDeclaration>>()
    else null

    for ((method, description) in methodsToOverride) {
      if (method.isFinal || method.isStatic || method.javaPsi.hasModifierProperty(PsiModifier.PRIVATE)) {
        methodsToAttachToClassFix?.add(method.createUastSmartPointer())

        val methodFixes = createFixesIfApplicable(method, method.name)
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
      if ((methodsToOverride.isNotEmpty() || classReasonToBeSubclassed != null)) {
        problemTargets(aClass, classHighlightableModifiersSet).forEach {
          problems.add(manager.createProblemDescriptor(
            it, classReasonToBeSubclassed ?: JavaAnalysisBundle.message("inspection.implicit.subclass.display.forClass", psiClass.name),
            isOnTheFly,
            createFixesIfApplicable(aClass, psiClass.name ?: "class", methodsToAttachToClassFix ?: emptyList()),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          )
        }
      }
    }

    return problems.toTypedArray()
  }

  private fun createFixesIfApplicable(aClass: UDeclaration,
                                      hintTargetName: String,
                                      methodsToAttachToClassFix: List<UastSmartPointer<UDeclaration>> = emptyList()): Array<LocalQuickFix> {
    val fix = MakeExtendableFix(aClass, hintTargetName, methodsToAttachToClassFix)
    if (!fix.hasActionsToPerform) return emptyArray()
    return arrayOf(fix)
  }

  private fun problemTargets(declaration: UDeclaration, highlightableModifiersSet: Set<String>): List<PsiElement> {
    val modifiersElements = getRelatedJavaModifiers(declaration, highlightableModifiersSet)
    if (modifiersElements.isNotEmpty()) return modifiersElements
    return listOfNotNull(declaration.uastAnchor?.sourcePsi)
  }

  private fun getRelatedJavaModifiers(declaration: UDeclaration,
                                      highlightableModifiersSet: Set<String>): List<PsiElement> {
    val modifierList = (declaration.sourcePsi as? PsiModifierListOwner)?.modifierList ?: return emptyList()
    return modifierList.children.filter { it is PsiKeyword && highlightableModifiersSet.contains(it.getText()) }
  }

  private val methodHighlightableModifiersSet = setOf(PsiModifier.FINAL, PsiModifier.PRIVATE, PsiModifier.STATIC)

  private val classHighlightableModifiersSet = setOf(PsiModifier.FINAL, PsiModifier.PRIVATE)

  private class MakeExtendableFix(uDeclaration: UDeclaration,
                                  hintTargetName: String,
                                  val siblings: List<UastSmartPointer<UDeclaration>> = emptyList())
    : LocalQuickFixOnPsiElement(uDeclaration.sourcePsi!!) {

    companion object {
      private val LOG = Logger.getInstance("#com.intellij.codeInspection.inheritance.MakeExtendableFix")
    }

    private val actionsToPerform = SmartList<IntentionAction>()

    val hasActionsToPerform: Boolean get() = actionsToPerform.isNotEmpty()

    init {
      collectMakeExtendable(uDeclaration, actionsToPerform)
      for (sibling in siblings) {
        sibling.element?.let {
          collectMakeExtendable(it, actionsToPerform, checkParent = false)
        }
      }
    }

    override fun getFamilyName(): String = QuickFixBundle.message("fix.modifiers.family")

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
      try {
        for (intentionAction in actionsToPerform) {
          if (intentionAction.isAvailable(project, null, file))
            intentionAction.invoke(project, null, file)
        }
      }
      catch (e: IncorrectOperationException) {
        if (ApplicationManager.getApplication().isUnitTestMode)
          throw e
        ApplicationManager.getApplication().invokeLater {
          Messages.showErrorDialog(project, e.message, CommonBundle.getErrorTitle())
        }
        LOG.info(e)
      }
    }

    private fun collectMakeExtendable(declaration: UDeclaration,
                                      actionsList: SmartList<IntentionAction>,
                                      checkParent: Boolean = true) {
      val isClassMember = declaration !is JvmClass
      addIfApplicable(declaration, JvmModifier.FINAL, false, actionsList)
        addIfApplicable(declaration, JvmModifier.PRIVATE, false, actionsList)
        if (isClassMember) {
          addIfApplicable(declaration, JvmModifier.STATIC, false, actionsList)
        }
      if (checkParent && isClassMember) {
        (declaration.uastParent as? UClass)?.apply {
          addIfApplicable(this, JvmModifier.FINAL, false, actionsList)
          addIfApplicable(this, JvmModifier.PRIVATE, false, actionsList)
        }
      }
    }

    private fun addIfApplicable(declaration: JvmModifiersOwner,
                                modifier: JvmModifier,
                                shouldPresent: Boolean,
                                actionsList: SmartList<IntentionAction>) {
      if (declaration.hasModifier(modifier) == shouldPresent) return
      val request = modifierRequest(modifier, shouldPresent)
      actionsList += createModifierActions(declaration, request)
    }

    private val MAX_MESSAGES_TO_COMBINE = 3

    private val text = when (uDeclaration) {
      is UClass ->
        if (actionsToPerform.size <= MAX_MESSAGES_TO_COMBINE)
          actionsToPerform.joinToString { it.text }
        else JavaAnalysisBundle.message("inspection.implicit.subclass.make.class.extendable",
                                       hintTargetName,
                                       siblings.size,
                                       siblingsDescription())
      else ->
        if (actionsToPerform.size <= MAX_MESSAGES_TO_COMBINE)
          actionsToPerform.joinToString { it.text }
        else
          JavaAnalysisBundle.message("inspection.implicit.subclass.extendable", hintTargetName)
    }

    private fun siblingsDescription() =
      when (siblings.size) {
        1 -> "'${(siblings.firstOrNull()?.element?.javaPsi as? JvmNamedElement)?.name}'"
        else -> ""
      }

    override fun getText(): String = text

  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
    override fun visitElement(element: PsiElement) {
      super.visitElement(element)
      val uClass = element.toUElementOfType<UClass>() ?: return
      val problems = checkClass(uClass, holder.manager, isOnTheFly) ?: return
      for (problem in problems) {
        holder.registerProblem(problem)
      }
    }
  }

}