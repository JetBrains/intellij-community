// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.json.JsonBundle
import com.intellij.json.psi.JsonObject
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker

open class AddOptionalPropertiesIntention : IntentionAction {
  override fun startInWriteAction(): Boolean {
    return false
  }

  override fun getFamilyName(): String {
    return JsonBundle.message("intention.add.not.required.properties.family.name")
  }

  override fun getText(): String {
    return JsonBundle.message("intention.add.not.required.properties.text")
  }

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    val containingObject = findContainingObjectNode(editor, file) ?: return false
    val missingProperties = collectMissingPropertiesFromSchema(containingObject, containingObject.project)
    return missingProperties != null && !missingProperties.hasOnlyRequiredPropertiesMissing
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val containingObject = findContainingObjectNode(editor, file) ?: return
    val missingProperties = collectMissingPropertiesFromSchema(containingObject, containingObject.project)
                              ?.missingKnownProperties ?: return

    WriteCommandAction.runWriteCommandAction(file.project, Computable {
      AddMissingPropertyFix(missingProperties, getSyntaxAdapter(project))
        .performFix(containingObject, Ref.create())
      ReformatCodeProcessor(containingObject.containingFile, false).run()
    })
  }

  protected open fun findContainingObjectNode(editor: Editor, file: PsiFile): PsiElement? {
    val offset = editor.caretModel.offset
    return file.findElementAt(offset)?.parentOfType<JsonObject>(false)
  }

  protected open fun getSyntaxAdapter(project: Project): JsonLikeSyntaxAdapter {
    return JsonOriginalPsiWalker.INSTANCE.getSyntaxAdapter(project)
  }
}