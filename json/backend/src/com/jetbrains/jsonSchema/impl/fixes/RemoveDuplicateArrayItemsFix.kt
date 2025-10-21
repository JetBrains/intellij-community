// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.json.JsonBundle
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parents
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker

class RemoveDuplicateArrayItemsFix(private val indices: IntArray) : PsiUpdateModCommandQuickFix() {
  override fun getFamilyName(): @IntentionFamilyName String =
    JsonBundle.message("remove.duplicated.items")

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val walker = JsonLikePsiWalker.getWalker(element, null) ?: return
    val parentArray = element.parents(true).firstNotNullOfOrNull {
      walker.createValueAdapter(it)?.asArray
    } ?: return
    val elementsToDelete = parentArray.elements.filterIndexed { index, _ -> index in indices }
      .mapNotNull { it.delegate }
    for (it in elementsToDelete) {
      if (!it.textRange.intersects(element.textRange)) {
        walker.getSyntaxAdapter(project)?.removeArrayItem(it)
      }
    }
  }
}