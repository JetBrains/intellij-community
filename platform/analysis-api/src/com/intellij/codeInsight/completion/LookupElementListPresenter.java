// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface LookupElementListPresenter {
  /**
   * An additional prefix is a prefix a part of completion prefix that was typed after the completion process had been started.
   * It is stored separately because we don't always restart completion process, and in this case we have a base prefix and additional prefix.
   */
  @NotNull
  String getAdditionalPrefix();

  @Nullable
  LookupElement getCurrentItem();

  @Nullable
  LookupElement getCurrentItemOrEmpty();

  /**
   * @return true if selection was touched by the user meaning that we want to preserve it
   */
  boolean isSelectionTouched();

  int getSelectedIndex();

  int getLastVisibleIndex();

  @NotNull
  LookupFocusDegree getLookupFocusDegree();

  boolean isShown();
}
