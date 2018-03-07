// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.*
import com.intellij.lang.jvm.actions.CreateAnnotationRequest
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager


class CreateAnnotationAction(target: PsiModifierListOwner, override val request: CreateAnnotationRequest) :
  CreateTargetAction<PsiModifierListOwner>(target, request) {

  override fun getText(): String = "Add @" + StringUtilRt.getShortName(request.qualifiedName); // TODO: i11n

  override fun getFamilyName(): String = "create annotation family " // TODO: i11n

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {

    val modifierList = target.modifierList ?: return

    if (!FileModificationService.getInstance().preparePsiElementForWrite(modifierList)) return

    WriteCommandAction.writeCommandAction(modifierList.project).run<RuntimeException> {
      val annotation = modifierList.addAnnotation(request.qualifiedName)

      val support = LanguageAnnotationSupport.INSTANCE.forLanguage(annotation.language)
      val psiLiteral = annotation.setDeclaredAttributeValue("value", support!!
        .createLiteralValue(request.valueLiteralValue, annotation))

      val formatter = CodeStyleManager.getInstance(project)
      val codeStyleManager = JavaCodeStyleManager.getInstance(project)
      codeStyleManager.shortenClassReferences(formatter.reformat(annotation))

      val editor = getEditor(target)
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

      val manager = TemplateManager.getInstance(target.project)
      val template = createTemplate(psiLiteral)
      manager.startTemplate(editor, template)

    }


  }

  private fun createTemplate(psiLiteral: PsiLiteral): Template {
    val builder = TemplateBuilderImpl(psiLiteral.containingFile)

    val textRange = psiLiteral.textRange
    val valueText = ElementManipulators.getValueText(psiLiteral)

    val from = TextRange.from(textRange.startOffset + 1, valueText.length)
    builder.replaceRange(from, createSuggestExpression(psiLiteral))
    return builder.buildInlineTemplate()
  }

  private fun createSuggestExpression(psiLiteral: PsiLiteral): Expression {
    return object : Expression() {
      override fun calculateResult(context: ExpressionContext): Result {
        PsiDocumentManager.getInstance(context.project).commitAllDocuments()
        return TextResult(ElementManipulators.getValueText(psiLiteral))
      }

      override fun calculateQuickResult(context: ExpressionContext): Result? = calculateResult(context)

      override fun calculateLookupItems(context: ExpressionContext): Array<LookupElement>? {
        return psiLiteral.references.filter { !it.isSoft }
          .flatMap { it.variants.mapNotNull { it as? LookupElement } }
          .sortedBy { it.lookupString }
          .toTypedArray()
      }
    }
  }

  companion object {

    fun getEditor(modifierListOwner: PsiModifierListOwner): Editor {
      val psiFile = modifierListOwner.containingFile
      val project = psiFile.project
      val virtualFile = psiFile.virtualFile!!
      return FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, virtualFile, 0), false)!!
    }
  }

}

