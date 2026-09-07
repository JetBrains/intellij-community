// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.setupEditor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.setupMethodBody
import com.intellij.lang.java.request.CreateMethodFromJavaUsageRequest
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateAbstractMethodActionGroup
import com.intellij.lang.jvm.actions.CreateMethodActionGroup
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.lang.jvm.actions.JvmGroupModCommandAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.ModTemplateBuilder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.JavaElementKind
import com.intellij.psi.util.PsiUtil.setModifierProperty
import com.intellij.psi.util.createSmartPointer
import org.jetbrains.annotations.ApiStatus

/**
 * @param abstract whether this action creates a method with explicit abstract modifier
 */
@ApiStatus.Internal
public class CreateMethodAction(
  targetClass: PsiClass,
  override val request: CreateMethodRequest,
  private val abstract: Boolean
) : CreateMemberModCommandAction(targetClass, request), JvmGroupModCommandAction {

  override fun getActionGroup(): JvmActionGroup = if (abstract) CreateAbstractMethodActionGroup else CreateMethodActionGroup

  override fun getRenderData(): JvmActionGroup.RenderData = JvmActionGroup.RenderData { request.methodName }

  override fun getTarget(): JvmClass? = targetClass

  override fun isAvailable(target: PsiClass): Boolean {
    return PsiNameHelper.getInstance(target.project).isIdentifier(request.methodName)
  }

  override fun getFamilyName(): String = message("create.method.from.usage.family")

  override fun getText(target: PsiClass): String {
    val what = request.methodName
    val where = getNameForClass(target, false)
    val kind = if (abstract) JavaElementKind.ABSTRACT_METHOD else JavaElementKind.METHOD
    return message("create.element.in.class", kind.`object`(), what, where)
  }

  override fun invoke(context: ActionContext, element: PsiClass, updater: ModPsiUpdater) {
    val originalTarget = targetClass ?: return
    JavaMethodRenderer(context.project, abstract, element, originalTarget, copyRequest(originalTarget, updater), updater).doMagic()
  }

  /**
   * Every write goes to a copy of the file. The expected types of the method come from the call, and the
   * call must be in the same copy to see the new method. So the request needs the copy of the call.
   *
   * @return the request against the copy of the call, or the original request when the call is in another
   * file, which the action does not change
   */
  private fun copyRequest(originalTarget: PsiClass, updater: ModPsiUpdater): CreateMethodRequest {
    val javaRequest = request as? CreateMethodFromJavaUsageRequest ?: return request
    if (javaRequest.call.containingFile != originalTarget.containingFile) return request
    return CreateMethodFromJavaUsageRequest(updater.getWritable(javaRequest.call), javaRequest.modifiers)
  }
}

/**
 * @param targetClass the writable copy of the target class
 * @param originalTarget the physical target class, to find the method again after the template ends
 */
private class JavaMethodRenderer(
  val project: Project,
  val abstract: Boolean,
  val targetClass: PsiClass,
  originalTarget: PsiClass,
  val request: CreateMethodRequest,
  val updater: ModPsiUpdater,
) {

  val factory = JavaPsiFacade.getElementFactory(project)!!
  val requestedModifiers = request.modifiers
  val javaUsage = request as? CreateMethodFromJavaUsageRequest

  private val originalTargetPointer = originalTarget.createSmartPointer(project)

  fun doMagic() {
    // The request holds the copy of the call when the call and the target class share a file, so the
    // anchor and the context are already writable. Otherwise the call is in another file, which gives
    // no anchor, and the context serves only as a resolve scope.
    val anchor = javaUsage?.getAnchor(targetClass)
    val guesserContext = javaUsage?.context
    // Take every writable copy before the first write, as ModPsiUpdater.getWritable demands.
    val elementToReplace = updater.getWritable(request.elementToReplace)

    var method = renderMethod()
    method = insertMethod(method, anchor, elementToReplace)
    setupTemplate(method, guesserContext)
  }

  fun renderMethod(): PsiMethod {
    val method = factory.createMethodFromText("<__TMP__> __TMP__ ${request.methodName}() {}", null)

    val modifiersToRender = requestedModifiers.toMutableList()
    if (targetClass.isInterface) {
      modifiersToRender -= (visibilityModifiers + JvmModifier.ABSTRACT)
    }
    else if (abstract) {
      if (modifiersToRender.remove(JvmModifier.PRIVATE)) {
        modifiersToRender += JvmModifier.PROTECTED
      }
      modifiersToRender += JvmModifier.ABSTRACT
    }

    for (modifier in modifiersToRender) {
      setModifierProperty(method, modifier.toPsiModifier(), true)
    }

    val factory = PsiElementFactory.getInstance(project)

    for (annotation in request.annotations) {
      val psiAnotation = method.modifierList.addAnnotation(annotation.qualifiedName)

      annotation.attributes.forEach {
        val value = CreateAnnotationActionUtil.attributeRequestToValue(it.value, factory, null)
        psiAnotation.setDeclaredAttributeValue(it.name, value)
      }
    }

    val shouldHaveBody = !abstract && (!targetClass.isInterface || JvmModifier.STATIC in requestedModifiers)
    if (!shouldHaveBody) method.body?.delete()

    return method
  }

  private fun insertMethod(method: PsiMethod, anchor: PsiElement?, elementToReplace: PsiElement?): PsiMethod {
    val inserted = if (anchor != null) {
      targetClass.addAfter(method, anchor)
    }
    else if (elementToReplace != null && elementToReplace.isValid) {
      elementToReplace.replace(method) as PsiMethod
    }
    else {
      targetClass.add(method)
    }
    return inserted as PsiMethod
  }

  /**
   * Every PSI change happens first, and the template fields go in at the end. A
   * [ModTemplateBuilder] writes the value of a field into the document at once, which leaves the PSI
   * behind, so an offset read after that is stale.
   */
  private fun setupTemplate(method: PsiMethod, guesserContext: PsiElement?) {
    method.typeParameters.forEach { typeParameter -> typeParameter.delete() }

    // Record the fields of the return type and of the parameters, and add the dummy parameters.
    val fields = RecordedTemplateFields(project)
    val substitutor = request.targetSubstitutor.toPsiSubstitutor(project)
    templateContext(project, factory, targetClass, fields, substitutor, guesserContext).run {
      setupTypeElement(method.returnTypeElement, request.returnType)
      setupParameters(method, request.expectedParameters)
    }

    // Write the fields now. The body text depends on the return type, and the method carries a
    // placeholder return type until the field writes the default value.
    val methodPointer = method.createSmartPointer(project)
    val builder = updater.templateBuilder()
    fields.flushTo(builder)
    PsiDocumentManager.getInstance(project).commitDocument(updater.document)
    val liveMethod = methodPointer.element ?: return

    // Build the body now, so a preview and a non-interactive run are already correct.
    setupBody(liveMethod, updater)

    if (liveMethod.containingClass?.rBrace == null) {
      val codeBlock = liveMethod.body
      if (codeBlock != null) {
        builder.finishAt((codeBlock.lBrace ?: codeBlock).textRange.startOffset)
      }
    }
    else {
      builder.finishAt(updater.caretOffset)
    }

    builder.onTemplateFinished { _ -> correctMethod() }
  }

  /**
   * Renders the body of the method and puts the caret in it. [setupMethodBody] replaces the body, so a
   * second call gives the same result as the first one.
   */
  private fun setupBody(method: PsiMethod, updater: ModPsiUpdater) {
    if (method.body == null && !method.hasModifierProperty(PsiModifier.DEFAULT)) return
    setupMethodBody(method, updater)
    val body = method.body ?: return
    setupEditor(body, updater)
  }

  /**
   * Corrects what the live template left behind. The user can select a vararg parameter type, and a
   * return type which needs another return statement. The template also moves the caret to the finish
   * offset, which drops the selection that [setupBody] made.
   */
  private fun correctMethod(): ModCommand {
    val target = originalTargetPointer.element ?: return ModCommand.nop()
    val method = target.findMethodsByName(request.methodName, false).singleOrNull() ?: return ModCommand.nop()
    return ModCommand.psiUpdate(method) { writableMethod, methodUpdater ->
      deleteParametersAfterVararg(writableMethod)
      setupBody(writableMethod, methodUpdater)
    }
  }

  private fun deleteParametersAfterVararg(method: PsiMethod) {
    var vararg = false
    for (parameter in method.parameterList.parameters) {
      if (vararg) {
        parameter.delete()
      }
      else if (parameter.isVarArgs) {
        vararg = true
      }
    }
  }
}
