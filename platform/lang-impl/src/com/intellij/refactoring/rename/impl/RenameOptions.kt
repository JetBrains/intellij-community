// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.psi.search.SearchScope

/**
 * @param renameTextOccurrences `null` means the option is not supported
 * @param renameCommentsStringsOccurrences `null` means the option is not supported
 */
internal data class RenameOptions(
  val renameTextOccurrences: Boolean?,
  val renameCommentsStringsOccurrences: Boolean?,
  val searchScope: SearchScope
)
