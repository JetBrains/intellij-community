// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface LookupElementListPresenter {
  @NotNull
  String getAdditionalPrefix();

  @Nullable
  LookupElement getCurrentItem();

  @Nullable
  LookupElement getCurrentItemOrEmpty();

  boolean isSelectionTouched();

  int getSelectedIndex();

  int getLastVisibleIndex();

  @NotNull
  LookupFocusDegree getLookupFocusDegree();

  boolean isShown();
}
