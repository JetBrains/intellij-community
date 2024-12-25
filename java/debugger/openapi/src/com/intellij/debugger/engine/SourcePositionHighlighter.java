// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

/**
 * During indexing, only extensions that implement {@link com.intellij.openapi.project.DumbAware} are called.
 * See also {@link DumbService}.
 *
 * @author Nikolay.Tropin
 */
public abstract class SourcePositionHighlighter implements PossiblyDumbAware {
  public static final ExtensionPointName<SourcePositionHighlighter> EP_NAME =
    ExtensionPointName.create("com.intellij.debugger.sourcePositionHighlighter");

  public abstract TextRange getHighlightRange(SourcePosition sourcePosition);

  public static @Nullable TextRange getHighlightRangeFor(SourcePosition sourcePosition) {
    for (SourcePositionHighlighter provider : DumbService.getDumbAwareExtensions(sourcePosition.getFile().getProject(), EP_NAME)) {
      TextRange range = provider.getHighlightRange(sourcePosition);
      if (range != null) {
        return range;
      }
    }
    return null;
  }
}
