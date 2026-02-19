// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.rd.util.lifetime.Lifetime
import org.jetbrains.annotations.ApiStatus

class CodeVisionListData @ApiStatus.Internal constructor(
  val lifetime: Lifetime,
  @get:ApiStatus.Internal
  val projectModel: ProjectCodeVisionModel,
  val rangeCodeVisionModel: RangeCodeVisionModel,
  val inlay: Inlay<*>,
  val anchoredLens: List<CodeVisionEntry>,
  val anchor: CodeVisionAnchorKind
) {
  companion object {
    @JvmField
    val KEY: Key<CodeVisionListData> = Key.create("CodeVisionListData")

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

  val visibleLens: ArrayList<CodeVisionEntry> = ArrayList()

  init {
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

  fun state(): RangeCodeVisionModel.InlayState = rangeCodeVisionModel.state()
  fun isMoreLensActive(): Boolean = Registry.`is`("editor.codeVision.more.inlay")
}

