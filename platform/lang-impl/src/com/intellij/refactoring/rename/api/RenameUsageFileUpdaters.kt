// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("RenameUsageFileUpdaters")

package com.intellij.refactoring.rename.api

import com.intellij.refactoring.rename.api.ModifiableRenameUsage.FileUpdater
import com.intellij.refactoring.rename.impl.DefaultPsiRenameUsageUpdater

internal typealias TextReplacement = (newName: String) -> String?

/**
 * Updater which updates usage [range][PsiRenameUsage.range] with new text set to new name as is.
 * This updater may be returned only from [ModifiableRenameUsage]s which implement [PsiRenameUsage].
 * Renames usages which return this updater are eligible for updates during inplace rename.
 */
fun idFileRangeUpdater(): FileUpdater = DefaultPsiRenameUsageUpdater
