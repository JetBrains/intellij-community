// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Internal interface for creating spacing instances.
 */
@ApiStatus.Internal
public interface SpacingFactory {
  @NotNull Spacing createSpacing(int minSpaces, int maxSpaces, int minLineFeeds, boolean keepLineBreaks, int keepBlankLines);

  @NotNull Spacing getReadOnlySpacing();

  @NotNull Spacing createDependentLFSpacing(int minSpaces,
                                            int maxSpaces,
                                            @NotNull TextRange dependencyRange,
                                            boolean keepLineBreaks,
                                            int keepBlankLines,
                                            @NotNull DependentSpacingRule rule);


  @NotNull Spacing createDependentLFSpacing(int minSpaces,
                                            int maxSpaces,
                                            @NotNull List<TextRange> dependencyRange,
                                            boolean keepLineBreaks,
                                            int keepBlankLines,
                                            @NotNull DependentSpacingRule rule);


  @NotNull Spacing createSafeSpacing(boolean keepLineBreaks, int keepBlankLines);

  @NotNull Spacing createKeepingFirstColumnSpacing(final int minSpaces,
                                                   final int maxSpaces,
                                                   final boolean keepLineBreaks,
                                                   final int keepBlankLines);

  @NotNull Spacing createSpacing(final int minSpaces,
                                 final int maxSpaces,
                                 final int minLineFeeds,
                                 final boolean keepLineBreaks,
                                 final int keepBlankLines,
                                 final int prefLineFeeds);
}
