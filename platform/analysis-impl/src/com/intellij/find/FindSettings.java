// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.find;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FindSettings {

  public static FindSettings getInstance() {
    return ApplicationManager.getApplication().getService(FindSettings.class);
  }

  public abstract boolean isSkipResultsWithOneUsage();

  public abstract void setSkipResultsWithOneUsage(boolean skip);

  public abstract @Nls String getDefaultScopeName();

  public abstract void setDefaultScopeName(String scope);

  public abstract boolean isSearchOverloadedMethods();

  public abstract void setSearchOverloadedMethods(boolean search);

  public abstract boolean isForward();

  public abstract void setForward(boolean findDirectionForward);

  public abstract boolean isFromCursor();

  public abstract void setFromCursor(boolean findFromCursor);

  public abstract boolean isGlobal();

  public abstract void setGlobal(boolean findGlobalScope);

  public abstract boolean isCaseSensitive();

  public abstract void setCaseSensitive(boolean caseSensitiveSearch);

  public abstract boolean isLocalCaseSensitive();

  public abstract void setLocalCaseSensitive(boolean caseSensitiveSearch);

  public abstract boolean isPreserveCase();

  public abstract void setPreserveCase(boolean preserveCase);

  public abstract boolean isWholeWordsOnly();

  public abstract void setWholeWordsOnly(boolean wholeWordsOnly);

  public abstract boolean isLocalWholeWordsOnly();

  public abstract void setLocalWholeWordsOnly(boolean wholeWordsOnly);

  public abstract boolean isRegularExpressions();

  public abstract void setRegularExpressions(boolean regularExpressions);

  public abstract boolean isLocalRegularExpressions();

  public abstract void setLocalRegularExpressions(boolean regularExpressions);

  /**
   * Returns the list of file masks used by the user in the "File name filter"
   * group box.
   *
   * @return the recent file masks list
   */
  public abstract @NlsSafe String @NotNull [] getRecentFileMasks();

  public abstract void setWithSubdirectories(boolean b);

  public abstract void initModelBySetings(@NotNull FindModel model);

  public abstract @Nullable @NlsSafe String getFileMask();

  public abstract void setFileMask(@Nullable @NlsSafe String fileMask);

  public abstract void setCustomScope(String scopeName);

  public abstract String getCustomScope();

  public abstract boolean isInStringLiteralsOnly();

  public abstract void setInStringLiteralsOnly(boolean selected);

  public abstract boolean isInCommentsOnly();

  public abstract void setInCommentsOnly(boolean selected);

  public abstract boolean isExceptStringLiterals();

  public abstract void setExceptStringLiterals(boolean selected);

  public abstract boolean isExceptComments();

  public abstract void setExceptComments(boolean selected);

  public abstract boolean isExceptCommentsAndLiterals();

  public abstract void setExceptCommentsAndLiterals(boolean selected);

  public abstract boolean isShowResultsInSeparateView();

  public abstract void setShowResultsInSeparateView(boolean selected);
}
