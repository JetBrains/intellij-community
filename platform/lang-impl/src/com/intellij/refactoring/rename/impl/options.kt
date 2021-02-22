// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.openapi.components.*
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
  val textOptions = textOptionsService().options[target.javaClass.name]
  return TextOptions(
    if (!canRenameTextOccurrences) null else textOptions?.renameTextOccurrences ?: true,
    if (!canRenameCommentAndStringOccurrences) null else textOptions?.renameCommentsStringsOccurrences ?: true,
  )
}

internal fun setTextOptions(target: RenameTarget, textOptions: TextOptions) {
  val options = textOptionsService().options
  if (emptyTextOptions == textOptions) {
    options.remove(target.javaClass.name)
  }
  else {
    options[target.javaClass.name] = textOptions
  }
}

private fun textOptionsService(): TextOptionsService = service()

@Service
@State(name = "TextOptionsService", storages = [Storage("renameTextOptions.xml")])
internal class TextOptionsService : PersistentStateComponent<TextOptionsService.State> {

  class State(var entries: List<StateEntry> = ArrayList())

  class StateEntry(
    var fqn: String = "",
    var textOccurrences: Boolean? = null,
    var commentsStringsOccurrences: Boolean? = null,
  )

  val options: MutableMap<String, TextOptions> = HashMap() // key is RenameTarget class FQN

  override fun getState(): State = State(
    options.map { (fqn, textOptions) ->
      StateEntry(fqn, textOptions.renameTextOccurrences, textOptions.renameCommentsStringsOccurrences)
    }
  )

  override fun loadState(state: State) {
    options.clear()
    for (entry in state.entries) {
      options[entry.fqn] = TextOptions(entry.textOccurrences, entry.commentsStringsOccurrences)
    }
  }
}
