/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.textCompletion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
public class ValuesCompletionProvider<T> implements TextCompletionProvider {
  @NotNull protected final TextCompletionValueDescriptor<T> myDescriptor;
  @NotNull private final List<Character> mySeparators;
  @NotNull protected final Collection<? extends T> myValues;
  private final boolean myCaseSensitive;
  @NotNull private final InsertHandler<LookupElement> myInsertHandler = new CompletionCharInsertHandler();

  /**
   * Create a completion provider.
   *
   * @param descriptor descriptor for completion values (text, icons, etc).
   * @param separators characters that separate values in the editor (like new line or space). If user is supposed to choose only one value this list should be empty.
   * @param values values to show in completion.
   * @param caseSensitive is completion case-sensitive.
   */
  public ValuesCompletionProvider(@NotNull TextCompletionValueDescriptor<T> descriptor,
                                  @NotNull List<Character> separators,
                                  @NotNull Collection<? extends T> values, boolean caseSensitive) {
    myDescriptor = descriptor;
    mySeparators = separators;
    myValues = values;
    myCaseSensitive = caseSensitive;
  }

  /**
   * Creates a completion provider for selecting single value from a list of values. Completion is case-insensitive.
   * @param presentation descriptor for completion values.
   * @param values list of values.
   */
  public ValuesCompletionProvider(@NotNull TextCompletionValueDescriptor<T> presentation,
                                  @NotNull Collection<? extends T> values) {
    this(presentation, Collections.emptyList(), values, false);
  }

  @Nullable
  @Override
  public String getAdvertisement() {
    return "";
  }

  @Nullable
  @Override
  public String getPrefix(@NotNull String text, int offset) {
    return getPrefix(text, offset, mySeparators);
  }

  @NotNull
  protected static String getPrefix(@NotNull String text, int offset, @NotNull Collection<Character> separators) {
    int index = -1;
    for (char c : separators) {
      index = Math.max(text.lastIndexOf(c, offset - 1), index);
    }
    return text.substring(index + 1, offset);
  }

  @NotNull
  @Override
  public CompletionResultSet applyPrefixMatcher(@NotNull CompletionResultSet result, @NotNull String prefix) {
    CompletionResultSet resultWithMatcher = result.withPrefixMatcher(new PlainPrefixMatcher(prefix));
    if (!myCaseSensitive) resultWithMatcher = resultWithMatcher.caseInsensitive();
    return resultWithMatcher;
  }

  @Override
  @Nullable
  public CharFilter.Result acceptChar(char c) {
    if (!mySeparators.contains(c)) return CharFilter.Result.ADD_TO_PREFIX;
    return CharFilter.Result.HIDE_LOOKUP;
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                     @NotNull String prefix,
                                     @NotNull CompletionResultSet result) {
    Collection<? extends T> values = getValues(prefix, result);
    values = ContainerUtil.sorted(values, myDescriptor);

    for (T completionVariant : values) {
      result.addElement(installInsertHandler(myDescriptor.createLookupBuilder(completionVariant)));
    }
    result.stopHere();
  }

  @NotNull
  protected LookupElement installInsertHandler(@NotNull LookupElementBuilder builder) {
    InsertHandler<LookupElement> handler = builder.getInsertHandler();
    if (handler == null) return builder.withInsertHandler(myInsertHandler);
    return builder.withInsertHandler(new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(InsertionContext context, LookupElement item) {
        myInsertHandler.handleInsert(context, item);
        handler.handleInsert(context, item);
      }
    });
  }

  @NotNull
  protected Collection<? extends T> getValues(@NotNull String prefix, @NotNull CompletionResultSet result) {
    return myValues;
  }

  public class CompletionCharInsertHandler implements InsertHandler<LookupElement> {
    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
      context.setAddCompletionChar(mySeparators.contains(context.getCompletionChar()));
    }
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
