// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassFromNewFix.setupSuperCall
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils
import com.intellij.codeInsight.generation.OverrideImplementUtil
import com.intellij.lang.java.request.CreateConstructorFromJavaUsageRequest
import com.intellij.lang.jvm.actions.CreateConstructorRequest
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.createSmartPointer

internal class CreateConstructorAction(
  target: PsiClass,
  override val request: CreateConstructorRequest,
) : CreateMemberModCommandAction(target, request) {

  override fun getFamilyName(): String = message("create.constructor.family")

  override fun getText(target: PsiClass): String = if (request is CreateConstructorFromJavaUsageRequest) {
    message("create.constructor.from.new.text")
  }
  else {
    message("create.constructor.text", getNameForClass(target, false))
  }

  override fun invoke(context: ActionContext, element: PsiClass, updater: ModPsiUpdater) {
    val originalTarget = targetClass ?: return
    JavaConstructorRenderer(context.project, element, originalTarget, request, updater).doMagic()
  }
}

/**
 * @param targetClass the writable copy of the target class
 * @param originalTarget the physical target class, to find the constructor again after the template ends
 */
private class JavaConstructorRenderer(
  private val project: Project,
  private val targetClass: PsiClass,
  originalTarget: PsiClass,
  private val request: CreateConstructorRequest,
  private val updater: ModPsiUpdater,
) {

  private val factory = JavaPsiFacade.getElementFactory(project)!!

  private val originalTargetPointer = originalTarget.createSmartPointer(project)

  /**
   * The constructors which the class already has. The action adds one more, and after the template
   * ends we tell them apart to find the new one.
   */
  private val constructorsBefore: List<SmartPsiElementPointer<PsiMethod>> =
    originalTarget.constructors.map { it.createSmartPointer(project) }

  fun doMagic() {
    //calculate expected parameter types before constructor is inserted
    //to avoid possible overload conflicts
    val parameters = request.expectedParameters
    // Take every writable copy before the first write, as ModPsiUpdater.getWritable demands.
    val guesserContext = updater.getWritable((request as? CreateConstructorFromJavaUsageRequest)?.context)

    var constructor = renderConstructor()
    constructor = insertConstructor(constructor)

    // Every PSI change happens first, and the template fields go in at the end. A ModTemplateBuilder
    // writes the value of a field into the document at once, which leaves the PSI behind. An offset
    // read after that is stale, and it can overlap another field, which TemplateBuilderImpl rejects.
    val fields = RecordedTemplateFields(project)
    val substitutor = request.targetSubstitutor.toPsiSubstitutor(project)
    templateContext(project, factory, targetClass, fields, substitutor, guesserContext)
      .setupParameters(constructor, parameters)
    val superConstructor = setupSuperCall(targetClass, constructor)

    // Write the fields now. The body repeats the parameter names in a super call, and a parameter
    // carries a placeholder name until the field writes the suggested name.
    val constructorPointer = constructor.createSmartPointer(project)
    val builder = updater.templateBuilder()
    fields.flushTo(builder)
    PsiDocumentManager.getInstance(project).commitDocument(updater.document)
    val liveConstructor = constructorPointer.element ?: return

    // Build the body now, so a preview and a run without a template are already correct.
    setupBody(liveConstructor, superConstructor, updater)
    builder.finishAt(updater.caretOffset)

    val superConstructorPointer = superConstructor?.createSmartPointer(project)
    builder.onTemplateFinished { _ -> correctConstructor(superConstructorPointer) }
  }

  fun renderConstructor(): PsiMethod {
    val constructor = factory.createConstructor()

    for (modifier in request.modifiers) {
      PsiUtil.setModifierProperty(constructor, modifier.toPsiModifier(), true)
    }

    val formatter = CodeStyleManager.getInstance(project)
    val codeStyleManager = JavaCodeStyleManager.getInstance(project)
    for (annotation in request.annotations) {
      val psiAnnotation = constructor.modifierList.addAnnotation(annotation.qualifiedName)
      codeStyleManager.shortenClassReferences(formatter.reformat(psiAnnotation))
    }

    return constructor
  }

  private fun insertConstructor(constructor: PsiMethod): PsiMethod {
    return targetClass.add(constructor) as PsiMethod
  }

  /**
   * Renders the body of the constructor and puts the caret in it. Both body setup methods replace the
   * body, so a second call gives the same result as the first one.
   *
   * @param superConstructor the super constructor whose arguments the body must pass, or null when the
   * body stays empty
   */
  private fun setupBody(constructor: PsiMethod, superConstructor: PsiMethod?, updater: ModPsiUpdater) {
    if (superConstructor == null) {
      CreateFromUsageUtils.setupMethodBody(constructor, updater)
    }
    else {
      val containingClass = constructor.containingClass ?: return
      OverrideImplementUtil.setupMethodBody(constructor, superConstructor, containingClass)
    }
    constructor.body?.let { body -> CreateFromUsageUtils.setupEditor(body, updater) }
  }

  /**
   * Rebuilds the body of the constructor. A super call repeats the parameter names, and the user can
   * rename a parameter in the template. The template also moves the caret to the finish offset, which
   * drops the selection that [setupBody] made.
   *
   * @param superConstructorPointer the super constructor which [setupBody] used, or null when the body
   * stays empty
   */
  private fun correctConstructor(superConstructorPointer: SmartPsiElementPointer<PsiMethod>?): ModCommand {
    val superConstructor = superConstructorPointer?.element
    if (superConstructorPointer != null && superConstructor == null) return ModCommand.nop()
    val originalTarget = originalTargetPointer.element ?: return ModCommand.nop()
    val constructor = findCreatedConstructor(originalTarget) ?: return ModCommand.nop()
    return ModCommand.psiUpdate(constructor) { writableConstructor, constructorUpdater ->
      setupBody(writableConstructor, superConstructor, constructorUpdater)
    }
  }

  private fun findCreatedConstructor(originalTarget: PsiClass): PsiMethod? {
    val candidates = originalTarget.constructors
    if (candidates.size == 1) return candidates.single()
    val before = constructorsBefore.mapNotNull { it.element }
    val manager = originalTarget.manager
    return candidates.singleOrNull { candidate -> before.none { manager.areElementsEquivalent(it, candidate) } }
  }
}
