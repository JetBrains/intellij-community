// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.*
import com.intellij.openapi.editor.Inlay
import com.intellij.util.ui.JBUI.scale

class BlockInlayRenderer(
  factory: PresentationFactory,
  constrainedPresentations: Collection<ConstrainedPresentation<*, BlockConstraints>>
) : LinearOrderInlayRenderer<BlockConstraints>(
  constrainedPresentations = constrainedPresentations,
  createPresentation = { createPresentation(factory, it) },
  comparator = compareBy({ it.constraints?.group }, { it.constraints?.column }, { it.priority })
) {

  override fun isAcceptablePlacement(placement: Inlay.Placement): Boolean {
    return placement == Inlay.Placement.BELOW_LINE || placement == Inlay.Placement.ABOVE_LINE
  }
}

private fun createPresentation(
  factory: PresentationFactory,
  constrained: List<ConstrainedPresentation<*, BlockConstraints>>
): InlayPresentation {
  val grouped = constrained.groupBy { it.constraints?.group }
  val vertical = mutableListOf<InlayPresentation>()

  for ((group, presentations) in grouped) {
    if (group == null) continue

    // "closer to the text" means reversed priority inside group, as presentations are in the same line - see [InlayModel]
    vertical.add(makeGroup(factory, presentations.reversed()))
  }
  grouped[null]?.let { noGroupPresentations -> vertical.addAll(noGroupPresentations.map { it.root })}

  return vertical.singleOrNull() ?: VerticalListInlayPresentation(vertical)
}

private fun makeGroup(
  factory: PresentationFactory,
  presentations: List<ConstrainedPresentation<*, BlockConstraints>>
): InlayPresentation {
  val column = presentations.first().constraints?.column ?: 0
  val result = mutableListOf(factory.textSpacePlaceholder(column, true))

  for ((index, presentation) in presentations.withIndex()) {
    result.add(presentation.root)
    if (index < presentations.lastIndex) result.add(SpacePresentation(scale(16), 0))
  }

  return SequencePresentation(result)
}