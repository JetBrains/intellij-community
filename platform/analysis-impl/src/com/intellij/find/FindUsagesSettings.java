// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.find;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nls;

public interface FindUsagesSettings {
  static FindUsagesSettings getInstance() {
    return ApplicationManager.getApplication().getService(FindUsagesSettings.class);
  }

  boolean isSkipResultsWithOneUsage();

  void setSkipResultsWithOneUsage(boolean skip);

  @Nls String getDefaultScopeName();

  void setDefaultScopeName(String scope);

  boolean isSearchOverloadedMethods();

  void setSearchOverloadedMethods(boolean search);

  boolean isShowResultsInSeparateView();

  void setShowResultsInSeparateView(boolean selected);
}
