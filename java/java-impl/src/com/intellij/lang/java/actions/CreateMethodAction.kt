// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.CodeInsightUtil.positionCursor
import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.setupEditor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.setupMethodBody
import com.intellij.codeInsight.daemon.impl.quickfix.GuessTypeParameters
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilder
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.lang.java.request.CreateMethodFromJavaUsageRequest
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.JavaElementKind
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil.setModifierProperty

/**
 * @param abstract whether this action creates a method with explicit abstract modifier
 */
internal class CreateMethodAction(
  targetClass: PsiClass,
  override val request: CreateMethodRequest,
  private val abstract: Boolean
) : CreateMemberAction(targetClass, request), JvmGroupIntentionAction {

  override fun getActionGroup(): JvmActionGroup = if (abstract) CreateAbstractMethodActionGroup else CreateMethodActionGroup

  override fun isAvailable(project: Project, file: PsiFile, target: PsiClass): Boolean {
    return super.isAvailable(project, file, target) && PsiNameHelper.getInstance(project).isIdentifier(request.methodName)
  }

  override fun getRenderData(): JvmActionGroup.RenderData = JvmActionGroup.RenderData { request.methodName }

  override fun getFamilyName(): String = message("create.method.from.usage.family")

  override fun getText(): String {
    val what = request.methodName
    val where = getNameForClass(target, false)
    val kind = if (abstract) JavaElementKind.ABSTRACT_METHOD else JavaElementKind.METHOD
    return message("create.element.in.class", kind.`object`(), what, where)
  }

  override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
    val copyClass = PsiTreeUtil.findSameElementInCopy(target, file)
    val previewRequest = if (request is CreateMethodFromJavaUsageRequest && request.call.containingFile == file.originalFile) {
      val copyCall = PsiTreeUtil.findSameElementInCopy(request.call, file) // copy call when possible to get proper anchor
      CreateMethodFromJavaUsageRequest(copyCall, request.modifiers)
    } else request
    JavaMethodRenderer(project, abstract, copyClass, previewRequest).doMagic()
    return IntentionPreviewInfo.DIFF
  }

  override fun invoke(project: Project, file: PsiFile, target: PsiClass) {
    JavaMethodRenderer(project, abstract, target, request).doMagic()
  }
}

private class JavaMethodRenderer(
  val project: Project,
  val abstract: Boolean,
  val targetClass: PsiClass,
  val request: CreateMethodRequest
) {

  val factory = JavaPsiFacade.getElementFactory(project)!!
  val requestedModifiers = request.modifiers
  val javaUsage = request as? CreateMethodFromJavaUsageRequest
  val withoutBody = abstract || targetClass.isInterface && JvmModifier.STATIC !in requestedModifiers

  fun doMagic() {
    var method = renderMethod()
    method = insertMethod(method)
    method = forcePsiPostprocessAndRestoreElement(method) ?: return
    val builder = setupTemplate(method)
    method = forcePsiPostprocessAndRestoreElement(method) ?: return
    val template = builder.buildInlineTemplate()
    startTemplate(method, template)
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

    for (annotation in request.annotations) {
      method.modifierList.addAnnotation(annotation.qualifiedName)
    }

    if (withoutBody) method.body?.delete()

    return method
  }

  private fun insertMethod(method: PsiMethod): PsiMethod {
    val anchor = javaUsage?.getAnchor(targetClass)
    val inserted = if (anchor == null) {
      targetClass.add(method)
    }
    else {
      targetClass.addAfter(method, anchor)
    }
    return inserted as PsiMethod
  }

  private fun setupTemplate(method: PsiMethod): TemplateBuilderImpl {
    val builder = TemplateBuilderImpl(method)
    createTemplateContext(builder).run {
      val returnType = request.returnType
      method.typeParameters.forEach { typeParameter -> typeParameter.delete() }
      setupTypeElement(method.returnTypeElement, returnType)
      setupParameters(method, request.expectedParameters)
    }
    if (method.containingClass?.rBrace == null) {
      val codeBlock = method.body
      if (codeBlock != null) {
        builder.setEndVariableBefore(codeBlock.lBrace ?: codeBlock)
      }
    }
    else {
      builder.setEndVariableAfter(method.body ?: method)
    }
    return builder
  }

  private fun createTemplateContext(builder: TemplateBuilder): TemplateContext {
    val substitutor = request.targetSubstitutor.toPsiSubstitutor(project)
    val guesser = GuessTypeParameters(project, factory, builder, substitutor)
    return TemplateContext(project, factory, targetClass, builder, guesser, javaUsage?.context)
  }

  private fun startTemplate(method: PsiMethod, template: Template) {
    val targetFile = targetClass.containingFile
    val newEditor = positionCursor(project, targetFile, method) ?: return
    val templateListener = if (withoutBody) null else MyMethodBodyListener(project, newEditor, targetFile)
    CreateFromUsageBaseFix.startTemplate(newEditor, template, project, templateListener, null)
  }
}

private class MyMethodBodyListener(val project: Project, val editor: Editor, val file: PsiFile) : TemplateEditingAdapter() {

  override fun templateFinished(template: Template, brokenOff: Boolean) {
    if (brokenOff) return
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
    val offset = editor.caretModel.offset
    val method = PsiTreeUtil.findElementOfClassAtOffset(file, offset - 1, PsiMethod::class.java, false) ?: return
    if (IntentionPreviewUtils.isIntentionPreviewActive()) {
      finishTemplate(method)
    } else {
      WriteCommandAction.runWriteCommandAction(project, message("create.method.body"), null, { finishTemplate(method) }, file)
    }
  }

  private fun finishTemplate(method: PsiMethod) {
    setupMethodBody(method)
    setupEditor(method, editor)
  }
}
