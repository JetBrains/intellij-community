// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassFromNewFix.setupSuperCall
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils
import com.intellij.codeInsight.daemon.impl.quickfix.GuessTypeParameters
import com.intellij.codeInsight.generation.OverrideImplementUtil
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilder
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.lang.java.request.CreateConstructorFromJavaUsageRequest
import com.intellij.lang.jvm.actions.CreateConstructorRequest
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.createSmartPointer

class CreateConstructorAction(targetClass: PsiClass, private val myRequest: CreateConstructorRequest) : BaseIntentionAction() {

  private val targetClassPointer = targetClass.createSmartPointer()

  override fun getFamilyName(): String = message("create.constructor.family")

  override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = targetClassPointer.element

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (targetClassPointer.element == null || !myRequest.isValid) return false
    text = message("create.constructor.from.new.text")
    return true
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val targetClass = targetClassPointer.element ?: error("Target class was invalidated between isAvailable() and invoke()")
    assert(myRequest.isValid) { "Request was invalidated between isAvailable() and invoke()" }
    JavaConstructorRenderer(project, targetClass, myRequest).doMagic()
  }
}

private class JavaConstructorRenderer(
  private val project: Project,
  private val targetClass: PsiClass,
  private val request: CreateConstructorRequest
) {

  private val factory = JavaPsiFacade.getElementFactory(project)!!

  fun doMagic() {
    var constructor = renderConstructor()
    constructor = insertConstructor(constructor)
    constructor = forcePsiPostprocessAndRestoreElement(constructor) ?: return

    val builder = TemplateBuilderImpl(constructor)
    createTemplateContext(builder).setupParameters(constructor, request.parameters)
    val superConstructor = setupSuperCall(targetClass, constructor, builder)

    constructor = forcePsiPostprocessAndRestoreElement(constructor) ?: return
    val template = builder.buildInlineTemplate()
    startTemplate(constructor, template, superConstructor)
  }

  private fun createTemplateContext(builder: TemplateBuilder): TemplateContext {
    val guesserContext = (request as? CreateConstructorFromJavaUsageRequest)?.context
    val substitutor = request.targetSubstitutor.toPsiSubstitutor(project)
    val guesser = GuessTypeParameters(project, factory, builder, substitutor)
    return TemplateContext(project, factory, targetClass, builder, guesser, guesserContext)
  }

  private fun renderConstructor(): PsiMethod {
    val constructor = factory.createConstructor()

    for (modifier in request.modifiers) {
      PsiUtil.setModifierProperty(constructor, modifier.toPsiModifier(), true)
    }

    for (annotation in request.annotations) {
      constructor.modifierList.addAnnotation(annotation.qualifiedName)
    }

    return constructor
  }

  private fun insertConstructor(constructor: PsiMethod): PsiMethod {
    return targetClass.add(constructor) as PsiMethod
  }

  private fun startTemplate(constructor: PsiMethod, template: Template, superConstructor: PsiMethod?) {
    val targetFile = targetClass.containingFile
    val targetEditor = CreateFromUsageBaseFix.positionCursor(project, targetFile, constructor) ?: return
    val templateListener = object : TemplateEditingAdapter() {

      override fun templateFinished(template: Template, brokenOff: Boolean) {
        if (brokenOff) return
        WriteCommandAction.runWriteCommandAction(project, this::setupBody)
      }

      private fun setupBody() {
        PsiDocumentManager.getInstance(project).commitDocument(targetEditor.document)
        val offset = targetEditor.caretModel.offset
        val newConstructor = PsiTreeUtil.findElementOfClassAtOffset(targetFile, offset - 1, PsiMethod::class.java, false) ?: return
        if (superConstructor == null) {
          CreateFromUsageUtils.setupMethodBody(newConstructor)
        }
        else {
          OverrideImplementUtil.setupMethodBody(newConstructor, superConstructor, targetClass)
        }
        CreateFromUsageUtils.setupEditor(newConstructor, targetEditor)
      }
    }
    CreateFromUsageBaseFix.startTemplate(targetEditor, template, project, templateListener, null)
  }
}
