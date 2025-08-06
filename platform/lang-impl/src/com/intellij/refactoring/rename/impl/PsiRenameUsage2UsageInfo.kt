// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.icons.AllIcons
import com.intellij.model.Pointer
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.api.ModifiableRenameUsage
import com.intellij.refactoring.rename.api.PsiRenameUsage
import com.intellij.refactoring.rename.api.RenameConflict
import com.intellij.usageView.UsageInfo
import javax.swing.Icon

/**
 * @param newName should not be stored and should come from outside
 * when it will be possible to update the new name without recomputing usages
 */
internal class PsiRenameUsage2UsageInfo(
  renameUsage: PsiRenameUsage,
  private val newName: String
) : UsageInfo(renameUsage.file, renameUsage.range, renameUsage is TextRenameUsage) {

  private val pointer: Pointer<out PsiRenameUsage> = renameUsage.createPointer()

  val renameUsage: PsiRenameUsage get() = requireNotNull(pointer.dereference())

  internal val isReadOnly: Boolean = renameUsage !is ModifiableRenameUsage

  override fun getIcon(): Icon? {
    if (renameUsage.conflicts(newName).isEmpty()) {
      return null
    }
    else {
      return AllIcons.General.Warning
    }
  }

  override fun getTooltipText(): String? {
    val conflicts = renameUsage.conflicts(newName)
    if (conflicts.isEmpty()) {
      return null
    }
    val singleConflict: RenameConflict? = conflicts.singleOrNull()
    if (singleConflict != null) {
      return singleConflict.description()
    }
    val conflictTags = conflicts.map { conflict ->
      HtmlChunk.text(conflict.description()).wrapWith("li")
    }
    return HtmlBuilder().append(
      HtmlChunk.text(RefactoringBundle.message("problems.detected.title")).bold()
    ).append(
      HtmlChunk.tag("ul").children(*conflictTags.toTypedArray())
    ).toString()
  }

  override fun isValid(): Boolean = super.isValid() && pointer.dereference() != null
}
