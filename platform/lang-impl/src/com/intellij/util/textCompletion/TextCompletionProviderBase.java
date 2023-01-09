// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.textCompletion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class TextCompletionProviderBase<T> implements TextCompletionProvider {
  @NotNull protected final TextCompletionValueDescriptor<T> myDescriptor;
  @NotNull private final List<Character> mySeparators;
  private final boolean myCaseSensitive;
  @NotNull private final InsertHandler<LookupElement> myInsertHandler = new CompletionCharInsertHandler();

  /**
   * Create a completion provider.
   *
   * @param descriptor    descriptor for completion values (text, icons, etc).
   * @param separators    characters that separate values in the editor (like new line or space). If user is supposed to choose only one value this list should be empty.
   * @param caseSensitive is completion case-sensitive.
   */
  public TextCompletionProviderBase(@NotNull TextCompletionValueDescriptor<T> descriptor,
                                    @NotNull List<Character> separators,
                                    boolean caseSensitive) {
    myDescriptor = descriptor;
    mySeparators = separators;
    myCaseSensitive = caseSensitive;
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
    Collection<? extends T> values = getValues(parameters, prefix, result);
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
    return builder.withInsertHandler(new InsertHandler<>() {
      @Override
      public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
        myInsertHandler.handleInsert(context, item);
        handler.handleInsert(context, item);
      }
    });
  }

  @NotNull
  protected abstract Collection<? extends T> getValues(@NotNull CompletionParameters parameters,
                                                       @NotNull String prefix,
                                                       @NotNull CompletionResultSet result);

  public class CompletionCharInsertHandler implements InsertHandler<LookupElement> {
    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
      context.setAddCompletionChar(mySeparators.contains(context.getCompletionChar()));
    }
  }
}
