// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.api.ReplaceTextTargetContext

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

internal fun renameOptions(project: Project, target: RenameTarget): RenameOptions {
  return RenameOptions(
    textOptions = getTextOptions(target),
    searchScope = target.maximalSearchScope ?: GlobalSearchScope.allScope(project)
  )
}

private val emptyTextOptions = TextOptions(null, null)

internal fun getTextOptions(target: RenameTarget): TextOptions {
  val canRenameTextOccurrences = !target.textTargets(ReplaceTextTargetContext.IN_PLAIN_TEXT).isEmpty()
  val canRenameCommentAndStringOccurrences = !target.textTargets(ReplaceTextTargetContext.IN_COMMENTS_AND_STRINGS).isEmpty()
  if (!canRenameTextOccurrences && !canRenameCommentAndStringOccurrences) {
    return emptyTextOptions
  }
  return TextOptions(
    if (!canRenameTextOccurrences) null else true,
    if (!canRenameCommentAndStringOccurrences) null else true,
  )
}
