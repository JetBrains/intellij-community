/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.InsertHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author peter
 */
public abstract class MutableLookupElement<T> extends LookupElement{
  @NotNull
  public abstract T getObject();

  public abstract MutableLookupElement<T> setBold();

  public abstract MutableLookupElement<T> setAutoCompletionPolicy(AutoCompletionPolicy policy);

  @NotNull
  public abstract MutableLookupElement<T> setIcon(Icon icon);

  @NotNull
  public abstract MutableLookupElement<T> setPriority(double priority);

  @NotNull
  public abstract MutableLookupElement<T> setGrouping(int grouping);

  @NotNull
  public abstract MutableLookupElement<T> setPresentableText(@NotNull String displayText);

  @NotNull
  public abstract MutableLookupElement<T> setTailType(@NotNull TailType type);

  @NotNull
  public abstract MutableLookupElement<T> setCaseSensitive(boolean caseSensitive);

  public abstract MutableLookupElement<T> addLookupStrings(@NonNls String... additionalLookupStrings);

  public abstract InsertHandler<? extends MutableLookupElement> getInsertHandler();
}
