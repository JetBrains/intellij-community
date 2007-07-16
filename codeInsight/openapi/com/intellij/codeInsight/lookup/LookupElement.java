/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.TailType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public interface LookupElement<T> {
  LookupElement[] EMPTY_ARRAY = new LookupElement[0];

  @NotNull
  T getObject();

  @NotNull
  String getLookupString();

  @NotNull
  TailType getTailType();

  @NotNull
  LookupElement<T> setTailType(@NotNull TailType type);

  @NotNull
  LookupElement<T> setIcon(@Nullable Icon icon);

  @NotNull
  LookupElement<T> setPresentableText(@NotNull String presentableText);

  /*@NotNull
  LookupElement setTypeText(@Nullable String text);*/

  @NotNull
  LookupElement<T> setCaseSensitive(boolean caseSensitive);

  LookupElement<T> setBold();
}
