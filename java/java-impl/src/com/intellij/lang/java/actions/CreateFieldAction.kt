// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.positionCursor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.startTemplate
import com.intellij.codeInsight.daemon.impl.quickfix.JavaCreateFieldFromUsageHelper
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.lang.java.request.CreateFieldFromJavaUsageRequest
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateFieldActionGroup
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil

internal class CreateFieldAction(target: PsiClass, request: CreateFieldRequest) : CreateFieldActionBase(target, request) {

  override fun getActionGroup(): JvmActionGroup = CreateFieldActionGroup

  override fun getText(): String = message("create.field.from.usage.full.text", request.fieldName, getNameForClass(target, false))

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    JavaFieldRenderer(project, false, target, request).doRender()
  }
}

internal val constantModifiers = setOf(
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
  private val expectedTypes = extractExpectedTypes(project, request.fieldType).toTypedArray()

  private val modifiersToRender: Collection<JvmModifier>
    get() {
      return if (constantField) {
        if (targetClass.isInterface) {
          // interface fields are public static final implicitly, so modifiers don't have to be rendered
          request.modifiers - constantModifiers - visibilityModifiers
        }
        else {
          // render static final explicitly
          request.modifiers + constantModifiers
        }
      }
      else {
        // render as is
        request.modifiers
      }
    }

  fun doRender() {
    var field = renderField()
    field = insertField(field)
    startTemplate(field)
  }

  private fun renderField(): PsiField {
    val field = JavaPsiFacade.getElementFactory(project).createField(request.fieldName, PsiType.INT)

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

    return field
  }

  private fun insertField(field: PsiField): PsiField {
    return helper.insertFieldImpl(targetClass, field, javaUsage?.anchor)
  }

  private fun startTemplate(field: PsiField) {
    val targetFile = targetClass.containingFile ?: return
    val newEditor = positionCursor(field.project, targetFile, field) ?: return
    val substitutor = request.targetSubstitutor.toPsiSubstitutor(project)
    val template = helper.setupTemplateImpl(field, expectedTypes, targetClass, newEditor, javaUsage?.reference, constantField, substitutor)
    val listener = MyTemplateListener(project, newEditor, targetFile)
    startTemplate(newEditor, template, project, listener, null)
  }
}

private class MyTemplateListener(val project: Project, val editor: Editor, val file: PsiFile) : TemplateEditingAdapter() {

  override fun templateFinished(template: Template, brokenOff: Boolean) {
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
    val offset = editor.caretModel.offset
    val psiField = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiField::class.java, false) ?: return
    runWriteAction {
      CodeStyleManager.getInstance(project).reformat(psiField)
    }
    editor.caretModel.moveToOffset(psiField.textRange.endOffset - 1)
  }
}
