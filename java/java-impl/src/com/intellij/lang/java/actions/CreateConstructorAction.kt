// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassFromNewFix.setupSuperCall
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils
import com.intellij.codeInsight.daemon.impl.quickfix.GuessTypeParameters
import com.intellij.codeInsight.generation.OverrideImplementUtil
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
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
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil

internal class CreateConstructorAction(
  target: PsiClass,
  override val request: CreateConstructorRequest
) : CreateMemberAction(target, request) {

  override fun getFamilyName(): String = message("create.constructor.family")

  override fun getText(): String = if (request is CreateConstructorFromJavaUsageRequest) {
    message("create.constructor.from.new.text")
  }
  else {
    message("create.constructor.text", getNameForClass(target, false))
  }

  private fun constructorRenderer(project: Project) = JavaConstructorRenderer(project, target, request)

  override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo {
    val copyClass = PsiTreeUtil.findSameElementInCopy(target, psiFile)
    val javaFieldRenderer = JavaConstructorRenderer(project, copyClass, request)
    javaFieldRenderer.doMagic()
    return IntentionPreviewInfo.DIFF
  }

  override fun invoke(project: Project, file: PsiFile, target: PsiClass) {
    constructorRenderer(project).doMagic()
  }
}

private class JavaConstructorRenderer(
  private val project: Project,
  private val targetClass: PsiClass,
  private val request: CreateConstructorRequest
) {

  private val factory = JavaPsiFacade.getElementFactory(project)!!

  fun doMagic() {
    //calculate expected parameter types before constructor is inserted 
    //to avoid possible overload conflicts
    val parameters = request.expectedParameters 
    var constructor = renderConstructor()
    constructor = insertConstructor(constructor)
    constructor = forcePsiPostprocessAndRestoreElement(constructor) ?: return

    val builder = TemplateBuilderImpl(constructor)
    builder.setScrollToTemplate(request.isStartTemplate)
    createTemplateContext(builder).setupParameters(constructor, parameters)
    val superConstructor = setupSuperCall(targetClass, constructor, builder)

    constructor = forcePsiPostprocessAndRestoreElement(constructor) ?: return
    if (request.isStartTemplate) {
      val template = builder.buildInlineTemplate()
      startTemplate(constructor, template, superConstructor)
    }
  }

  private fun createTemplateContext(builder: TemplateBuilder): TemplateContext {
    val guesserContext = (request as? CreateConstructorFromJavaUsageRequest)?.context
    val substitutor = request.targetSubstitutor.toPsiSubstitutor(project)
    val guesser = GuessTypeParameters(project, factory, builder, substitutor)
    return TemplateContext(project, factory, targetClass, builder, guesser, guesserContext)
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

  private fun startTemplate(constructor: PsiMethod, template: Template, superConstructor: PsiMethod?) {
    val targetFile = targetClass.containingFile
    val targetEditor = CodeInsightUtil.positionCursor(project, targetFile, constructor) ?: return
    val templateListener = object : TemplateEditingAdapter() {

      override fun templateFinished(template: Template, brokenOff: Boolean) {
        if (brokenOff) return
        if (IntentionPreviewUtils.isIntentionPreviewActive()) {
          setupBody()
        } else {
          WriteCommandAction.runWriteCommandAction(project, message("create.constructor.body.command"), null, { setupBody() }, targetFile)
        }
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
