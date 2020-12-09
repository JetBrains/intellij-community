// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.api

import com.intellij.util.text.StringOperation

/**
 * Updater which sets the new target name as the new text under the [range][PsiRenameUsage.range].
 * This updater may be returned only from [ModifiableRenameUsage]s which implement [PsiRenameUsage].
 */
object DefaultPsiRenameUsageUpdater : ModifiableRenameUsage.FileUpdater {

  override fun prepareFileUpdate(usage: ModifiableRenameUsage, newName: String): Collection<FileOperation> {
    usage as PsiRenameUsage
    return listOf(
      FileOperation.modifyFile(
        usage.file,
        StringOperation.replace(usage.range, newName)
      )
    )
  }
}
