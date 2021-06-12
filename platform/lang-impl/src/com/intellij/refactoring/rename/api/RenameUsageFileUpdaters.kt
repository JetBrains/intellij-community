// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("RenameUsageFileUpdaters")

package com.intellij.refactoring.rename.api

import com.intellij.refactoring.rename.api.ModifiableRenameUsage.FileUpdater
import com.intellij.refactoring.rename.impl.PsiRenameUsageRangeUpdater

internal val idFileRangeUpdater = fileRangeUpdater { it }

internal typealias UsageTextByName = (newName: String) -> String?

/**
 * Updater which updates usage [range][PsiRenameUsage.range] with new text set to new name as is.
 * This updater may be returned only from [ModifiableRenameUsage]s which implement [PsiRenameUsage].
 * Renames usages which return this updater are eligible for updates during inplace rename.
 */
fun idFileRangeUpdater(): FileUpdater = idFileRangeUpdater

/**
 * Updater which updates usage [range][PsiRenameUsage.range] with new text from [usageTextByName].
 * This updater may be returned only from [ModifiableRenameUsage]s which implement [PsiRenameUsage].
 * Renames usages which return this updater are eligible for updates during inplace rename.
 *
 * This updater can be used if the usage text differs from target name,
 * e.g., for `getFoo` reference to `foo` property this updater might look like
 * ```
 * fileRangeUpdater { newName ->
 *   "get" + newName.capitalize()
 * }
 * ```
 *
 * @param usageTextByName must be a stateless function which computes the new text of the usage by new name;
 * the function must not access indices or do any heavy computations because it might be executed in the UI thread synchronously (during inplace rename);
 * if for some reason it's impossible to implement such a function, then one should implement custom FileUpdater instead
 */
fun fileRangeUpdater(usageTextByName: UsageTextByName): FileUpdater = PsiRenameUsageRangeUpdater(usageTextByName)
