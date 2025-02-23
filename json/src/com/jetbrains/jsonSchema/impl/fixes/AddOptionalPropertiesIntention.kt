// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.json.JsonBundle
import com.intellij.json.psi.JsonObject
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.parentOfType
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter
import com.jetbrains.jsonSchema.extension.JsonSchemaQuickFixSuppressor
import com.jetbrains.jsonSchema.impl.JsonCachedValues
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    if (JsonSchemaQuickFixSuppressor.EXTENSION_POINT_NAME.extensionList.any {
      it.shouldSuppressFix(file, AddOptionalPropertiesIntention::class.java)
    }) return false
    return JsonCachedValues.hasComputedSchemaObjectForFile(containingObject.containingFile)
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    runWithModalProgressBlocking(project, JsonBundle.message("intention.add.not.required.properties.text")) {
      val (objectPointer, missingProperties) = readAction {
        val containingObject = findContainingObjectNode(editor, file)?.createSmartPointer() ?: return@readAction null
        val missingProperties = collectMissingPropertiesFromSchema(containingObject, containingObject.project)
                                  ?.missingKnownProperties ?: return@readAction null
        containingObject to missingProperties
      } ?: return@runWithModalProgressBlocking

      edtWriteAction {
        executeCommand {
          AddMissingPropertyFix(missingProperties, getSyntaxAdapter(project)).performFix(objectPointer.dereference())
        }
      }
      withContext(Dispatchers.EDT) {
        objectPointer.containingFile?.let { ReformatCodeProcessor(it, false).run() }
      }
    }
  }

  protected open fun findContainingObjectNode(editor: Editor, file: PsiFile): PsiElement? {
    val offset = editor.caretModel.offset
    return file.findElementAt(offset)?.parentOfType<JsonObject>(false)
  }

  protected open fun getSyntaxAdapter(project: Project): JsonLikeSyntaxAdapter {
    return JsonOriginalPsiWalker.INSTANCE.getSyntaxAdapter(project)
  }

  override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
    val containingObject = findContainingObjectNode(editor, file) ?: return IntentionPreviewInfo.EMPTY
    val missingProperties = collectMissingPropertiesFromSchema(containingObject.createSmartPointer(), containingObject.project)
                              ?.missingKnownProperties ?: return IntentionPreviewInfo.EMPTY
    AddMissingPropertyFix(missingProperties, getSyntaxAdapter(project))
      .performFixInner(containingObject, Ref.create())
    ReformatCodeProcessor(containingObject.containingFile, false).run()
    return IntentionPreviewInfo.DIFF
  }
}