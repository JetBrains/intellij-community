/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

/**
 * During indexing, only extensions that implement {@link com.intellij.openapi.project.DumbAware} are called.
 * See also {@link DumbService}.
 *
 * @author Nikolay.Tropin
 */
public abstract class SourcePositionHighlighter {
  public static final ExtensionPointName<SourcePositionHighlighter> EP_NAME = ExtensionPointName.create("com.intellij.debugger.sourcePositionHighlighter");

  public abstract TextRange getHighlightRange(SourcePosition sourcePosition);

  @Nullable
  public static TextRange getHighlightRangeFor(SourcePosition sourcePosition) {
    DumbService dumbService = DumbService.getInstance(sourcePosition.getFile().getProject());
    for (SourcePositionHighlighter provider : dumbService.filterByDumbAwareness(EP_NAME.getExtensions())) {
      TextRange range = provider.getHighlightRange(sourcePosition);
      if (range != null) {
        return range;
      }
    }
    return null;
  }
}
