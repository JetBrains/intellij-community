// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.patterns.ElementPattern;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Provides completion items.
 * <p>
 * Register via {@link CompletionContributor#extend(CompletionType, ElementPattern, CompletionProvider)}.
 *
 * @author peter
 */
public abstract class CompletionProvider<V extends CompletionParameters> {

  protected CompletionProvider() {
  }

  /**
   * @deprecated unused parameter
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  protected CompletionProvider(final boolean startInReadAction) {
  }

  protected abstract void addCompletions(@NotNull V parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result);

  public final void addCompletionVariants(@NotNull final V parameters,
                                          @NotNull ProcessingContext context,
                                          @NotNull final CompletionResultSet result) {
    addCompletions(parameters, context, result);
  }
}
