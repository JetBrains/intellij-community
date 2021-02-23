// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.api.ReplaceTextTargetContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RenameOptions(
  val textOptions: TextOptions,
  val searchScope: SearchScope,
)

/**
 * @param commentStringOccurrences `null` means the option is not supported
 * @param textOccurrences `null` means the option is not supported
 */
@ApiStatus.Internal
data class TextOptions(
  val commentStringOccurrences: Boolean?,
  val textOccurrences: Boolean?,
)

private val emptyTextOptions = TextOptions(null, null)

internal val TextOptions.isEmpty: Boolean get() = this == emptyTextOptions

internal fun renameOptions(project: Project, target: RenameTarget): RenameOptions {
  return RenameOptions(
    textOptions = getTextOptions(target),
    searchScope = target.maximalSearchScope ?: GlobalSearchScope.allScope(project)
  )
}

internal fun getTextOptions(target: RenameTarget): TextOptions {
  val canRenameCommentAndStringOccurrences = !target.textTargets(ReplaceTextTargetContext.IN_COMMENTS_AND_STRINGS).isEmpty()
  val canRenameTextOccurrences = !target.textTargets(ReplaceTextTargetContext.IN_PLAIN_TEXT).isEmpty()
  if (!canRenameCommentAndStringOccurrences && !canRenameTextOccurrences) {
    return emptyTextOptions
  }
  val textOptions = textOptionsService().options[target.javaClass.name]
  return TextOptions(
    commentStringOccurrences = if (!canRenameCommentAndStringOccurrences) null else textOptions?.commentStringOccurrences ?: true,
    textOccurrences = if (!canRenameTextOccurrences) null else textOptions?.textOccurrences ?: true,
  )
}

internal fun setTextOptions(target: RenameTarget, textOptions: TextOptions) {
  val options = textOptionsService().options
  if (textOptions.isEmpty) {
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
    var commentsStringsOccurrences: Boolean? = null,
    var textOccurrences: Boolean? = null,
  )

  val options: MutableMap<String, TextOptions> = HashMap() // key is RenameTarget class FQN

  override fun getState(): State = State(
    options.map { (fqn, textOptions) ->
      StateEntry(fqn, textOptions.commentStringOccurrences, textOptions.textOccurrences)
    }
  )

  override fun loadState(state: State) {
    options.clear()
    for (entry in state.entries) {
      options[entry.fqn] = TextOptions(commentStringOccurrences = entry.commentsStringsOccurrences, textOccurrences = entry.textOccurrences)
    }
  }
}
