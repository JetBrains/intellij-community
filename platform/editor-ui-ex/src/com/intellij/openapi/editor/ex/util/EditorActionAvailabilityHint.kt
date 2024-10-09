// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

private val logger = logger<EditorActionAvailabilityHint>()

/**
 * Availability of some actions can be checked with the presence of the caret inside some region in the editor.
 * Such a region can be represented as a highlighter, inlay, etc.
 * So a feature can mark some highlighter with a hint in order to be checked by an action later.
 * Example:
 *  Live templates add frame highlighters into an editor, and when a live template is active (a frame is here)
 *  the actions like Tab/Shift Tab don't insert tabs but navigate over the placeholders.
 *  For these purposes there are special action NextTemplateVariable/PreviousTemplateVariable that have higher priority
 *  but available only if the template session is active and the caret is placed inside one of such template frames (read 'highlighters')
 *  Using this mechanism we can precisely detect action availability in Remote Dev mode on a thin client.
 *
 *
 *  For this case we need to mark frame highlighters with hints in the following way:
 *  ```
 *   frameHighlighter.addActionAvailabilityHint(
 *        EditorActionAvailabilityHint("NextTemplateVariable", EditorActionAvailabilityHint.CaretInside),
 *        EditorActionAvailabilityHint("PreviousTemplateVariable", EditorActionAvailabilityHint.CaretInside)
 *   )
 *  ```
 */
@Experimental
class EditorActionAvailabilityHint @JvmOverloads constructor(val actionId: String, val condition: AvailabilityCondition, val backend: String = "remote") {

  enum class AvailabilityCondition {
    CaretInside {
      override fun isAvailable(offset: Int, rangeMarker: RangeMarker): Boolean = rangeMarker.textRange.containsOffset(offset)
    },
    CaretOnStart {
      override fun isAvailable(offset: Int, rangeMarker: RangeMarker): Boolean = rangeMarker.startOffset == offset
    },
    CaretOnEnd {
      override fun isAvailable(offset: Int, rangeMarker: RangeMarker): Boolean = rangeMarker.endOffset == offset
    };

    open fun isAvailable(offset: Int, rangeMarker: RangeMarker): Boolean = false
  }
}

/**
 * Marks [RangeMarker] with [EditorActionAvailabilityHint]
 *
 * NB: when marking a range marker you have to ensure that you do it inside highlighter initialization block.
 * See the last parameter of [com.intellij.openapi.editor.ex.MarkupModelEx.addRangeHighlighterAndChangeAttributes]
 */
@Experimental
fun RangeMarker.addActionAvailabilityHint(vararg hints: EditorActionAvailabilityHint) {
  this as UserDataHolderEx? ?: run {
    logger.error("Attempt to register ${EditorActionAvailabilityHint::class.simpleName} on ${RangeMarker::class.simpleName} which is not ${UserDataHolderEx::class.simpleName} ")
    return
  }
  this.addActionAvailabilityHintImpl(*hints)
}

/**
 * Marks [Inlay] with [EditorActionAvailabilityHint]
 *
 * NB: when marking an inlay you have to wrap any `com.intellij.openapi.editor.InlayModel.add*` call
 * with [com.intellij.openapi.editor.InlayModel.execute] to force listeners triggering occurs after [addActionAvailabilityHint] is called.
 */
@Experimental
fun Inlay<*>.addActionAvailabilityHint(vararg hints: EditorActionAvailabilityHint) {
  this as UserDataHolderEx? ?: run {
    logger.error("Attempt to register ${EditorActionAvailabilityHint::class.simpleName} on ${Inlay::class.simpleName} which is not ${UserDataHolderEx::class.simpleName} ")
    return
  }
  this.addActionAvailabilityHintImpl(*hints)
}

@Experimental
fun RangeHighlighter.clearAvailabilityHints() {
  clearAvailabilityHintsImpl()
}

@Experimental
fun Inlay<*>.clearAvailabilityHints() {
  clearAvailabilityHintsImpl()
}

@get:Internal
val RangeMarker.actionAvailabilityHints: List<EditorActionAvailabilityHint> get() {
  return getUserData(hintsKey) ?: emptyList()
}

@get:Internal
val Inlay<*>.actionAvailabilityHints: List<EditorActionAvailabilityHint> get() {
  return getUserData(hintsKey) ?: emptyList()
}

/**
 * Returns not null result if there is any highlighter under [offset] that has [EditorActionAvailabilityHint] for [actionId]
 * and the hint provides an availability value.
 *
 * Otherwise, returns `null` (means that there's no highlighters with a hint for [actionId]).
 */
@Internal
fun Editor.isActionAvailableByHint(offset: Int, actionId: String, backend: String = "remote"): Boolean? {
  val markupModel = markupModel
  if (markupModel !is MarkupModelEx) {
    return null
  }

  return markupModel.isActionAvailableByHint(offset, actionId, backend)
         ?: document.isActionAvailableByHint(project, offset, actionId, backend)
}

@Internal
fun Document.isActionAvailableByHint(project: Project?, offset: Int, actionId: String, backend: String): Boolean? {
  val markupModel = DocumentMarkupModel.forDocument(this, project, false)
  if (markupModel !is MarkupModelEx) {
    return null
  }

  return markupModel.isActionAvailableByHint(offset, actionId, backend)
}


@Internal
private fun MarkupModelEx.isActionAvailableByHint(offset: Int, actionId: String, backend: String): Boolean? {
  val overlappingIterator = overlappingIterator(offset, offset)
  try {
    for (highlighterEx in overlappingIterator) {
      for (actionAvailabilityHint in highlighterEx.actionAvailabilityHints) {
        if (actionAvailabilityHint.actionId == actionId && (actionAvailabilityHint.backend == "ANY" || actionAvailabilityHint.backend == backend)) {
          return actionAvailabilityHint.condition.isAvailable(offset, highlighterEx)
        }
      }
    }
  }
  finally {
    overlappingIterator.dispose()
  }
  return null
}

private fun UserDataHolderEx.addActionAvailabilityHintImpl(vararg newHints: EditorActionAvailabilityHint) {
  var hints = getUserData(hintsKey)
  if (hints == null) {
    hints = ContainerUtil.createConcurrentList()
    putUserDataIfAbsent(hintsKey, hints)
  }
  for (newHint in newHints) {
    val existingHint = hints.find { it.actionId == newHint.actionId }
    if (existingHint != null) {
      if (existingHint.condition != newHint.condition) {
        logger.error("Availability hint for action '${newHint.actionId}' already exists")
      }
      continue
    }

    hints.add(newHint)
  }
}

private fun UserDataHolder.clearAvailabilityHintsImpl() {
  putUserData(hintsKey, null)
}

private val hintsKey = Key<MutableList<EditorActionAvailabilityHint>>("EditorActionOnRangeMarkerAvailabilityHints")