// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layers

object MinimapLayerIds {
  val LEGACY_PREVIEW: MinimapLayerId = MinimapLayerId("legacyPreview")
  val TOKEN_FILLER: MinimapLayerId = MinimapLayerId("tokenFiller")
  val SELECTION: MinimapLayerId = MinimapLayerId("selection")
  val DIAGNOSTICS: MinimapLayerId = MinimapLayerId("diagnostics")
  val FOLD_MARKERS: MinimapLayerId = MinimapLayerId("foldMarkers")
  val BREAKPOINTS: MinimapLayerId = MinimapLayerId("breakpoints")
  val HOVER: MinimapLayerId = MinimapLayerId("hover")
  val CARET: MinimapLayerId = MinimapLayerId("caret")
  val THUMB: MinimapLayerId = MinimapLayerId("thumb")
}
