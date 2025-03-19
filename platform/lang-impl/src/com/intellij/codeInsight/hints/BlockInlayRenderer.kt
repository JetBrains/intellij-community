// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.InlayPresentationFactory.Padding
import com.intellij.codeInsight.hints.presentation.*
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.JBUI.scale
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent

class BlockInlayRenderer(
  factory: PresentationFactory,
  constrainedPresentations: Collection<ConstrainedPresentation<*, BlockConstraints>>
) : LinearOrderInlayRenderer<BlockConstraints>(
  constrainedPresentations = constrainedPresentations,
  createPresentation = { createPresentation(factory, it) },
  comparator = createComparator()
) {
  override fun isAcceptablePlacement(placement: Inlay.Placement): Boolean = isBlockAcceptablePlacement(placement)
}

private fun isBlockAcceptablePlacement(placement: Inlay.Placement): Boolean =
  placement == Inlay.Placement.BELOW_LINE || placement == Inlay.Placement.ABOVE_LINE

private fun createComparator(): Comparator<ConstrainedPresentation<*, BlockConstraints>> =
  compareBy({ it.constraints?.group }, { it.constraints?.column }, { it.priority })

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

/**
 * Updates padding during every inlay update.
 */
@ApiStatus.Internal
open class BlockInlayWithPaddingRenderer(
  private val paddingProvider: BlockInlayPaddingProvider,
  factory: PresentationFactory,
  constrainedPresentations: Collection<ConstrainedPresentation<*, BlockConstraints>>,
) : LinearOrderInlayRenderer<BlockConstraints>(
  constrainedPresentations = constrainedPresentations,
  createPresentation = { createPresentation(factory, it) },
  comparator = createComparator()
) {

  lateinit var inlay: Inlay<*>

  fun initialize(inlay: Inlay<*>) {
    assert(!::inlay.isInitialized) { "Inlay already defined for current renderer" }
    this.inlay = inlay
    inlay.update()
  }

  override fun isAcceptablePlacement(placement: Inlay.Placement): Boolean = isBlockAcceptablePlacement(placement)

  var padding: Padding = Padding(0, 0, 0, 0)
    private set

  override fun calcHeightInPixels(inlay: Inlay<*>): Int {
    padding = paddingProvider.updateVerticalPadding(inlay, padding)
    return padding.top + super.calcHeightInPixels(inlay) + padding.bottom
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    padding = paddingProvider.updateHorizontalPadding(inlay, padding)
    return padding.left + super.calcWidthInPixels(inlay) + padding.right
  }

  private fun applyPadding(targetRegion: Rectangle): Rectangle {
    val translated = Point(targetRegion.location.x + padding.left, targetRegion.location.y + padding.top)
    return Rectangle(translated.x, translated.y,
                     targetRegion.width - (padding.left + padding.right),
                     targetRegion.height - (padding.top + padding.bottom))
  }

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    val region = applyPadding(targetRegion)
    super.paint(inlay, g, region, textAttributes)
  }

  private fun isInsideActiveArea(translated: Point): Boolean {
    if (!::inlay.isInitialized)
      return false

    val bounds = inlay.bounds ?: return false
    val activeWidth = bounds.width - (padding.left + padding.right)
    val activeHeight = bounds.height - (padding.top + padding.bottom)
    val translatedToActiveArea = Point(translated.x - padding.left, translated.y - padding.top)

    return translatedToActiveArea.x in 0..activeWidth
           && translatedToActiveArea.y in 0..activeHeight
  }

  override fun mouseClicked(event: MouseEvent, translated: Point) {
    if (isInsideActiveArea(translated))
      super.mouseClicked(event, translated)
  }

  override fun mousePressed(event: MouseEvent, translated: Point) {
    if (isInsideActiveArea(translated))
      super.mousePressed(event, translated)
  }

  override fun mouseReleased(event: MouseEvent, translated: Point) {
    if (isInsideActiveArea(translated))
      super.mouseReleased(event, translated)
  }

  private var isMouseOverActiveArea: Boolean = false

  override fun mouseMoved(event: MouseEvent, translated: Point) {
    if (isInsideActiveArea(translated)) {
      isMouseOverActiveArea = true
      super.mouseMoved(event, translated)
    }
    else {
      val wasOver = isMouseOverActiveArea
      isMouseOverActiveArea = false

      if (wasOver)
        super.mouseExited()
    }
  }

  override fun mouseExited() { /* handling in mouseMoved */
  }
}