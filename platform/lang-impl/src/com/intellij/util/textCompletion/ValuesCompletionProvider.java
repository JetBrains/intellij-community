// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.textCompletion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Completion provider for a fixed collection of elements. See {@link ValuesCompletionProviderDumbAware} for dumb aware version.
 * <p>
 * Completion elements and their presentation (represented with {@link TextCompletionValueDescriptor}) are provided in constructor.
 * Use {@link TextFieldWithCompletion} to create a text field component with completion.
 * <p>
 * Completion is done via {@link com.intellij.util.TextFieldCompletionProvider}.
 *
 * @param <T> completion element type.
 */
public class ValuesCompletionProvider<T> extends TextCompletionProviderBase<T> {
  protected final @NotNull Collection<? extends T> myValues;

  /**
   * Create a completion provider.
   *
   * @param descriptor    descriptor for completion values (text, icons, etc).
   * @param separators    characters that separate values in the editor (like new line or space). If user is supposed to choose only one value this list should be empty.
   * @param values        values to show in completion.
   * @param caseSensitive is completion case-sensitive.
   */
  public ValuesCompletionProvider(@NotNull TextCompletionValueDescriptor<T> descriptor,
                                  @NotNull List<Character> separators,
                                  @NotNull Collection<? extends T> values,
                                  boolean caseSensitive) {
    super(descriptor, separators, caseSensitive);
    myValues = values;
  }

  /**
   * Creates a completion provider for selecting single value from a list of values. Completion is case-insensitive.
   *
   * @param presentation descriptor for completion values.
   * @param values       list of values.
   */
  public ValuesCompletionProvider(@NotNull TextCompletionValueDescriptor<T> presentation,
                                  @NotNull Collection<? extends T> values) {
    this(presentation, Collections.emptyList(), values, false);
  }

  @Override
  protected @NotNull Collection<? extends T> getValues(@NotNull CompletionParameters parameters,
                                                       @NotNull String prefix,
                                                       @NotNull CompletionResultSet result) {
    return myValues;
  }

  public static class ValuesCompletionProviderDumbAware<T> extends ValuesCompletionProvider<T> implements DumbAware {
    public ValuesCompletionProviderDumbAware(@NotNull TextCompletionValueDescriptor<T> descriptor,
                                             @NotNull List<Character> separators,
                                             @NotNull Collection<? extends T> values,
                                             boolean caseSensitive) {
      super(descriptor, separators, values, caseSensitive);
    }

    public ValuesCompletionProviderDumbAware(@NotNull TextCompletionValueDescriptor<T> presentation,
                                             @NotNull Collection<? extends T> values) {
      super(presentation, values);
    }
  }
}
