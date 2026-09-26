// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.refactoring.rename.api.FileOperation
import com.intellij.refactoring.rename.api.ModifiableRenameUsage
import com.intellij.refactoring.rename.api.PsiRenameUsage
import com.intellij.refactoring.rename.api.UsageTextByName
import com.intellij.util.text.StringOperation

internal class PsiRenameUsageRangeUpdater(
  val usageTextByName: UsageTextByName
) : ModifiableRenameUsage.FileUpdater {

  override fun prepareFileUpdate(usage: ModifiableRenameUsage, newName: String): Collection<FileOperation> {
    usage as PsiRenameUsage
    val newText = usageTextByName(newName) ?: return emptyList()
    return listOf(
      FileOperation.modifyFile(
        usage.file,
        StringOperation.replace(usage.range, newText)
      )
    )
  }
}
