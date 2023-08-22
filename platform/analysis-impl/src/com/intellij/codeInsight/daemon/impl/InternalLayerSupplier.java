// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.markup.HighlighterLayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Workaround for Rider being unable to work with standard layers but prefers its own custom layers for each HighlightInfo.
 * Please avoid; use standard layers from {@link HighlighterLayer} instead
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public interface InternalLayerSupplier {
  /**
   * @return layer hardcoded for this HighlightInfo, or -1 if this extension is not going to supply a layer for this HighlightInfo
   */
  int getLayer(@NotNull HighlightInfo info);
}
