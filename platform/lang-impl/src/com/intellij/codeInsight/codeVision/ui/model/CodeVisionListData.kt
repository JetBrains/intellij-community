package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.map
import com.jetbrains.rd.util.throttleLast
import java.time.Duration

class CodeVisionListData(
  val lifetime: Lifetime,
  val projectModel: ProjectCodeVisionModel,
  val rangeCodeVisionModel: RangeCodeVisionModel,
  val inlay: Inlay<*>,
  val anchoredLens: List<CodeVisionEntry>,
  val anchor: CodeVisionAnchorKind
) {
  companion object {
    @JvmField
    val KEY = Key.create<CodeVisionListData>("CodeVisionListData")

  }

  var isPainted: Boolean = false
    get() = field
    set(value) {
      if (field != value) {
        field = value
        if ((inlay.editor as? EditorImpl)?.isPurePaintingMode != true)
          inlay.update()
      }
    }

  val visibleLens = ArrayList<CodeVisionEntry>()
  private var throttle = false

  init {
    projectModel.hoveredInlay.map {
      it == inlay
    }.throttleLast(Duration.ofMillis(300), SwingScheduler).advise(lifetime) {
      throttle = it
      inlay.repaint()
    }

    updateVisible()
  }

  private fun updateVisible() {
    val count = projectModel.maxVisibleLensCount[anchor]

    visibleLens.clear()

    if (count == null) {
      visibleLens.addAll(anchoredLens)
      return
    }

    val visibleCount = minOf(count, anchoredLens.size)
    visibleLens.addAll(anchoredLens.subList(0, visibleCount))
  }

  fun state() = rangeCodeVisionModel.state()
  fun isMoreLensActive() = throttle && Registry.`is`("editor.codeVision.more.inlay")
  fun isHoveredEntry(entry: CodeVisionEntry) = projectModel.hoveredEntry.value == entry && projectModel.hoveredInlay.value == inlay
}

