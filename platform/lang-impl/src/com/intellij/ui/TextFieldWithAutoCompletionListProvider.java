/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Roman.Chernyatchik
 */
public abstract class TextFieldWithAutoCompletionListProvider<T> implements Comparator<T> {
  @NotNull protected Collection<T> myVariants;
  @Nullable
  private String myCompletionAdvertisement;

  @Nullable
  protected abstract Icon getIcon(@NotNull final T item);

  @NotNull
  protected abstract String getLookupString(@NotNull final T item);

  @Nullable
  protected abstract String getTailText(@NotNull final T item);

  @Nullable
  protected abstract String getTypeText(@NotNull final T item);

  @Override
  public abstract int compare(T item1, T item2);

  protected TextFieldWithAutoCompletionListProvider(@Nullable final Collection<T> variants) {
    setItems(variants);
    myCompletionAdvertisement = null;
  }

  public void setItems(@Nullable final Collection<T> variants) {
    myVariants = (variants != null) ? variants : Collections.<T>emptyList();
  }

  @NotNull
  public Collection<T> getItems(String prefix, boolean cached, CompletionParameters parameters) {
    if (prefix == null) {
      return Collections.emptyList();
    }

    final List<T> items = new ArrayList<T>(myVariants);

    Collections.sort(items, this);
    return items;
  }

  /**
   * Completion list advertisement for documentation popup
   *
   * @param shortcut
   * @return text
   */
  @Nullable
  public String getQuickDocHotKeyAdvertisement(@NotNull final String shortcut) {
    final String advertisementTail = getQuickDocHotKeyAdvertisementTail(shortcut);
    if (advertisementTail == null) {
      return null;
    }
    return "Pressing " + shortcut + " would show " + advertisementTail;
  }

  /**
   * Completion list advertisement text, if null advertisement for documentation
   * popup will be shown
   *
   * @return text
   */
  @Nullable
  public String getAdvertisement() {
    return myCompletionAdvertisement;
  }

  public void setAdvertisement(@Nullable final String completionAdvertisement) {
    myCompletionAdvertisement = completionAdvertisement;
  }

  @Nullable
  public PrefixMatcher createPrefixMatcher(@NotNull final String prefix) {
    return new PlainPrefixMatcher(prefix);
  }

  public LookupElementBuilder createLookupBuilder(@NotNull final T item) {
    LookupElementBuilder builder = LookupElementBuilder.create(item, getLookupString(item))
      .withIcon(getIcon(item));

    final InsertHandler<LookupElement> handler = createInsertHandler(item);
    if (handler != null) {
      builder = builder.withInsertHandler(handler);
    }

    final String tailText = getTailText(item);
    if (tailText != null) {
      builder = builder.withTailText(tailText, true);
    }

    final String typeText = getTypeText(item);
    if (typeText != null) {
      builder = builder.withTypeText(typeText);
    }
    return builder;
  }

  @Nullable
  public String getPrefix(@NotNull final CompletionParameters parameters) {
    return getCompletionPrefix(parameters);
  }

  public static String getCompletionPrefix(CompletionParameters parameters) {
    String text = parameters.getOriginalFile().getText();
    int offset = parameters.getOffset();
    int i = text.lastIndexOf(' ', offset - 1) + 1;
    int j = text.lastIndexOf('\n', offset - 1) + 1;
    return text.substring(Math.max(i, j), offset);
  }

  @Nullable
  protected String getQuickDocHotKeyAdvertisementTail(@NotNull final String shortcut) {
    return null;
  }

  @Nullable
  protected InsertHandler<LookupElement> createInsertHandler(@NotNull final T item) {
    return null;
  }
}
