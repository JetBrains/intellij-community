// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes

import com.intellij.json.JsonBundle
import com.intellij.json.psi.JsonObject
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter
import com.jetbrains.jsonSchema.extension.JsonSchemaQuickFixSuppressor
import com.jetbrains.jsonSchema.impl.JsonCachedValues
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly

open class AddOptionalPropertiesIntention : PsiUpdateModCommandAction<PsiElement>(PsiElement::class.java) {
  override fun getFamilyName(): @Nls(capitalization = Nls.Capitalization.Sentence) String =
    JsonBundle.message("intention.add.not.required.properties.family.name")

  // For tests and UI text lookup compatibility
  @TestOnly
  fun getText(): @Nls(capitalization = Nls.Capitalization.Sentence) String =
    JsonBundle.message("intention.add.not.required.properties.text")

  override fun getPresentation(context: ActionContext, element: PsiElement): Presentation? {
    val obj = findContainingObjectNode(context, element) ?: return null
    if (JsonSchemaQuickFixSuppressor.EXTENSION_POINT_NAME.extensionList.any {
        it.shouldSuppressFix(context.file(), AddOptionalPropertiesIntention::class.java)
      }) return null
    if (!JsonCachedValues.hasComputedSchemaObjectForFile(obj.containingFile)) return null

    val missing = collectMissingPropertiesFromSchema(obj.createSmartPointer(), context.project())?.missingKnownProperties
    if (missing == null || missing.myMissingPropertyIssues.isEmpty()) return null

    return Presentation.of(JsonBundle.message("intention.add.not.required.properties.text"))
  }

  override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater) {
    val objCopy = findContainingObjectNode(context, element) ?: return
    val physObj = findPhysicalObjectNode(context, element) ?: return

    val missing = collectMissingPropertiesFromSchema(physObj.createSmartPointer(), context.project())?.missingKnownProperties
                  ?: return

    AddMissingPropertyFix(missing, getSyntaxAdapter(context.project()))
      .performFixInner(objCopy, Ref.create())

    CodeStyleManager.getInstance(context.project()).reformatText(objCopy.containingFile, setOf(objCopy.textRange))
  }

  protected open fun getSyntaxAdapter(project: Project): JsonLikeSyntaxAdapter =
    JsonOriginalPsiWalker.INSTANCE.getSyntaxAdapter(project)

  protected open fun findPhysicalObjectNode(context: ActionContext, element: PsiElement): PsiElement? {
    val physLeaf = context.findLeaf() ?: return null
    return PsiTreeUtil.getParentOfType(physLeaf, JsonObject::class.java)
  }

  protected open fun findContainingObjectNode(context: ActionContext, element: PsiElement): PsiElement? {
    return PsiTreeUtil.getParentOfType(element, JsonObject::class.java)
           ?: PsiTreeUtil.getParentOfType(context.findLeaf(), JsonObject::class.java)
  }
}