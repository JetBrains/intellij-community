// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inheritance

import com.intellij.CommonBundle
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
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
import com.intellij.psi.PsiJvmConversionHelper.MODIFIERS
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.uast.UastSmartPointer
import com.intellij.uast.createUastSmartPointer
import com.intellij.util.IncorrectOperationException
import com.intellij.util.SmartList
import org.jetbrains.annotations.Nls
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

public class ImplicitSubclassInspection : LocalInspectionTool() {
  private val allModifiers = listOf(PsiModifier.PRIVATE, PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC)

  private fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val psiClass = aClass.javaPsi
    val classIsFinal = aClass.isFinal

    val problems = SmartList<ProblemDescriptor>()

    val subclassInfos = ImplicitSubclassProvider.EP_NAME.extensionList.asSequence().filter {
      it.isApplicableTo(psiClass)
    }.mapNotNull { it.getSubclassingInfo(psiClass) }

    val methodsToOverride = aClass.methods.mapNotNull { method ->
      subclassInfos
        .mapNotNull { it.methodsInfo?.get(method.javaPsi) }
        .firstOrNull()?.let { description ->
          method to description
        }
    }

    val methodsToAttachToClassFix = if (classIsFinal)
      SmartList<UastSmartPointer<UDeclaration>>()
    else null

    for ((method, overridingInfo) in methodsToOverride) {
      if (method.isFinal || method.isStatic || !overridingInfo.acceptedModifiers.any { method.javaPsi.hasModifier(it) }) {
        methodsToAttachToClassFix?.add(method.createUastSmartPointer())
        val methodFixes = createFixesIfApplicable(method, method.name, emptyList(), overridingInfo.acceptedModifiers)
        problemTargets(method, HashSet(methodHighlightableModifiersSet).apply { addAll(modifiersToHighlight(overridingInfo)) }).forEach {
          problems.add(manager.createProblemDescriptor(
            it, overridingInfo.description, isOnTheFly,
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

  @Suppress("ConvertArgumentToSet")
  private fun modifiersToHighlight(overridingInfo: ImplicitSubclassProvider.OverridingInfo): HashSet<String> {
    return HashSet(allModifiers).apply { removeAll(overridingInfo.acceptedModifiers.mapNotNull { MODIFIERS[it] }) }
  }

  private fun createFixesIfApplicable(aClass: UDeclaration,
                                      hintTargetName: String,
                                      siblings: List<UastSmartPointer<UDeclaration>> = emptyList(),
                                      acceptedModifiers: Array<JvmModifier> = arrayOf(JvmModifier.PUBLIC, JvmModifier.PACKAGE_LOCAL,
                                                                                      JvmModifier.PROTECTED)): Array<LocalQuickFix> {
    val fix = MakeExtendableFix(aClass, hintTargetName, siblings, acceptedModifiers)
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

  private val methodHighlightableModifiersSet = setOf(PsiModifier.FINAL, PsiModifier.STATIC)

  private val classHighlightableModifiersSet = setOf(PsiModifier.FINAL)

  private class MakeExtendableFix(uDeclaration: UDeclaration,
                                  hintTargetName: String,
                                  val siblings: List<UastSmartPointer<UDeclaration>> = emptyList(),
                                  acceptedModifiers: Array<JvmModifier> = arrayOf(JvmModifier.PUBLIC, JvmModifier.PACKAGE_LOCAL, JvmModifier.PROTECTED)
  )
    : LocalQuickFixOnPsiElement(uDeclaration.sourcePsi!!) {

    companion object {
      private val LOG = Logger.getInstance(MakeExtendableFix::class.java)
    }

    private val actionsToPerform = SmartList<IntentionAction>()

    val hasActionsToPerform: Boolean get() = actionsToPerform.isNotEmpty()

    init {
      collectMakeExtendable(uDeclaration, actionsToPerform, acceptedModifiers)
      for (sibling in siblings) {
        sibling.element?.let {
          collectMakeExtendable(it, actionsToPerform, acceptedModifiers, checkParent = false)
        }
      }
    }

    override fun getFamilyName(): String = QuickFixBundle.message("fix.modifiers.family")

    override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
      try {
        for (intentionAction in actionsToPerform) {
          if (intentionAction.isAvailable(project, null, psiFile))
            intentionAction.invoke(project, null, psiFile)
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

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
      val file = previewDescriptor.startElement.containingFile ?: return IntentionPreviewInfo.EMPTY
      val editor = IntentionPreviewUtils.getPreviewEditor() ?: return IntentionPreviewInfo.EMPTY
      for (intentionAction in actionsToPerform) {
        if (intentionAction.isAvailable(project, null, file)) {
          if (intentionAction.generatePreview(project, editor, file) != IntentionPreviewInfo.DIFF) {
            return IntentionPreviewInfo.EMPTY
          }
        }
      }
      return IntentionPreviewInfo.DIFF
    }

    private fun collectMakeExtendable(declaration: UDeclaration,
                                      actionsList: SmartList<IntentionAction>,
                                      acceptedModifiers: Array<JvmModifier>,
                                      checkParent: Boolean = true) {
      val isClassMember = declaration !is JvmClass
      addIfApplicable(declaration, JvmModifier.FINAL, false, actionsList)

      if (!acceptedModifiers.any { declaration.hasModifier(it) } && acceptedModifiers.isNotEmpty()) {
          addIfApplicable(declaration, acceptedModifiers[0], true, actionsList)
      }
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

    @Nls
    private val text = when (uDeclaration) {
      is UClass ->
        if (actionsToPerform.size <= MAX_MESSAGES_TO_COMBINE)
          actionsToPerform.filter { it.isAvailable(uDeclaration.project, null, uDeclaration.containingFile) }
            .joinToString { it.text }
        else JavaAnalysisBundle.message("inspection.implicit.subclass.make.class.extendable",
                                        hintTargetName,
                                        siblings.size,
                                        siblingsDescription())
      else ->
        if (actionsToPerform.size <= MAX_MESSAGES_TO_COMBINE)
          actionsToPerform.filter { it.isAvailable(uDeclaration.project, null, uDeclaration.containingFile) }
            .joinToString { it.text }
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

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {
      override fun visitClass(node: UClass): Boolean {
        val problems = checkClass(node, holder.manager, isOnTheFly)
        for (problem in problems) {
          holder.registerProblem(problem)
        }
        return true
      }
    }, arrayOf(UClass::class.java))
  }
}