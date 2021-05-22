// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find;

import com.intellij.openapi.actionSystem.DataKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public interface SearchSession {
  DataKey<SearchSession> KEY = DataKey.create("search.replace.session");

  @PropertyKey(resourceBundle = FindBundle.BUNDLE) String INCORRECT_REGEXP_MESSAGE_KEY = "find.incorrect.regexp";

  /** @deprecated please use {@link FindBundle#message} with {@link #INCORRECT_REGEXP_MESSAGE_KEY} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @Nls String INCORRECT_REGEX_MESSAGE = FindBundle.message(INCORRECT_REGEXP_MESSAGE_KEY);

  @NotNull FindModel getFindModel();

  @NotNull SearchReplaceComponent getComponent();

  boolean hasMatches();

  void searchForward();

  void searchBackward();

  void close();

  default boolean isSearchInProgress() {
    return false;
  }
}
