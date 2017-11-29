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
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.positionCursor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.startTemplate
import com.intellij.codeInsight.daemon.impl.quickfix.JavaCreateFieldFromUsageHelper
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.lang.java.request.CreateFieldFromJavaUsageRequest
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil

internal class CreateFieldAction(
  targetClass: PsiClass,
  request: CreateFieldRequest,
  private val constantField: Boolean
) : CreateFieldActionBase(targetClass, request) {

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    val targetClass = myTargetClass.element ?: return false
    if (!myRequest.isValid) return false

    val fieldName = myRequest.fieldName

    val canRender = run {
      val requestedModifiers = myRequest.modifiers
      val constantRequested = myRequest.constant || targetClass.isInterface || requestedModifiers.containsAll(constantModifiers)
      if (constantField) {
        constantRequested || fieldName.toUpperCase() == fieldName
      }
      else {
        !constantRequested
      }
    }
    if (!canRender) return false

    val className = getNameForClass(targetClass, false)
    text = if (constantField) {
      message("create.constant.from.usage.full.text", fieldName, className)
    }
    else {
      message("create.field.from.usage.full.text", fieldName, className)
    }
    return true
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val targetClass = myTargetClass.element ?: return
    assert(myRequest.isValid)
    JavaFieldRenderer(project, constantField, targetClass, myRequest).doRender()
  }
}

private val constantModifiers = setOf(
  JvmModifier.STATIC,
  JvmModifier.FINAL
)

private class JavaFieldRenderer(
  val project: Project,
  val constantField: Boolean,
  val targetClass: PsiClass,
  val request: CreateFieldRequest
) {

  val helper = JavaCreateFieldFromUsageHelper()
  val javaUsage = request as? CreateFieldFromJavaUsageRequest
  val expectedTypes = extractExpectedTypes(project, request.fieldType).toTypedArray()

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
