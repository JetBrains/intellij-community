// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.psi.search.SearchScope

internal class RenameOptions(
  val textOptions: TextOptions,
  val searchScope: SearchScope,
)

/**
 * @param renameTextOccurrences `null` means the option is not supported
 * @param renameCommentsStringsOccurrences `null` means the option is not supported
 */
internal class TextOptions(
  val renameTextOccurrences: Boolean?,
  val renameCommentsStringsOccurrences: Boolean?,
)
