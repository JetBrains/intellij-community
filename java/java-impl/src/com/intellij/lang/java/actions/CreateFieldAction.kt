// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.CodeInsightUtil.positionCursor
import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.startTemplate
import com.intellij.codeInsight.daemon.impl.quickfix.JavaCreateFieldFromUsageHelper
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.lang.java.request.CreateFieldFromJavaUsageRequest
import com.intellij.lang.jvm.JvmLong
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateFieldActionGroup
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.JavaElementKind
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil

internal class CreateFieldAction(target: PsiClass, request: CreateFieldRequest) : CreateFieldActionBase(target, request) {

  override fun getActionGroup(): JvmActionGroup = CreateFieldActionGroup

  override fun getText(): String = message("create.element.in.class", JavaElementKind.FIELD.`object`(),
                                           request.fieldName, getNameForClass(target, false))
}

internal val constantModifiers: Set<JvmModifier> = setOf(
  JvmModifier.STATIC,
  JvmModifier.FINAL
)

internal class JavaFieldRenderer(
  private val project: Project,
  private val constantField: Boolean,
  private val targetClass: PsiClass,
  private val request: CreateFieldRequest
) {

  private val helper = JavaCreateFieldFromUsageHelper() // TODO get rid of it
  private val javaUsage = request as? CreateFieldFromJavaUsageRequest
  private val expectedTypes = extractExpectedTypes(project, request.fieldType, targetClass).toTypedArray()

  private val modifiersToRender: Collection<JvmModifier>
    get() {
      return if (constantField) {
        // render static final explicitly
        request.modifiers + constantModifiers
      }
      else {
        // render as is
        request.modifiers
      }
    }

  fun doRender() {
    var field = renderField()
    field = insertField(field, javaUsage?.anchor)
    if (request.fieldType.isEmpty() || request.fieldType.size > 1 || request.isStartTemplate) {
      startTemplate(field)
    }
  }

  fun renderField(): PsiField {
    val fieldType = if (expectedTypes.isNotEmpty()) expectedTypes[0].type else PsiTypes.intType()
    val field = JavaPsiFacade.getElementFactory(project).createField(request.fieldName, fieldType)

    // clean template modifiers
    field.modifierList?.let { list ->
      list.firstChild?.let {
        list.deleteChildRange(it, list.lastChild)
      }
    }

    // setup actual modifiers
    for (modifier in modifiersToRender.map(JvmModifier::toPsiModifier)) {
      PsiUtil.setModifierProperty(field, modifier, true)
    }

    field.modifierList?.let { modifierList ->
      for (annRequest in request.annotations) {
        modifierList.addAnnotation(annRequest.qualifiedName)
      }
    }

    val requestInitializer = request.initializer
    if (requestInitializer is JvmLong) {
      field.initializer = PsiElementFactory.getInstance(project).createExpressionFromText("${requestInitializer.longValue}L", null)
    }

    return field
  }

  internal fun insertField(field: PsiField, anchor: PsiElement?): PsiField {
    return helper.insertFieldImpl(targetClass, field, anchor)
  }

  internal fun startTemplate(field: PsiField) {
    val targetFile = targetClass.containingFile ?: return
    val newEditor = positionCursor(field.project, targetFile, field) ?: return
    val substitutor = request.targetSubstitutor.toPsiSubstitutor(project)
    val template = helper.setupTemplateImpl(field, expectedTypes, targetClass, newEditor, javaUsage?.reference, constantField, request.isStartTemplate, substitutor)
    val listener = MyTemplateListener(project, newEditor, targetFile)
    startTemplate(newEditor, template, project, listener, null)
  }
}

private class MyTemplateListener(val project: Project, val editor: Editor, val file: PsiFile) : TemplateEditingAdapter() {

  override fun templateFinished(template: Template, brokenOff: Boolean) {
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
    val offset = editor.caretModel.offset
    val psiField = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiField::class.java, false) ?: return
    IntentionPreviewUtils.write<RuntimeException> {
      CodeStyleManager.getInstance(project).reformat(psiField)
    }
    editor.caretModel.moveToOffset(psiField.textRange.endOffset - 1)
  }
}
