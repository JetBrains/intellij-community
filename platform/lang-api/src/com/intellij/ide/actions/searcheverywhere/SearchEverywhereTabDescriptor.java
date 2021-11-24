// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated to be removed in IDEA 2022.2
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
public final class SearchEverywhereTabDescriptor {

  public static final SearchEverywhereTabDescriptor PROJECT = new SearchEverywhereTabDescriptor("SearchEverywhere.Project");
  public static final SearchEverywhereTabDescriptor IDE = new SearchEverywhereTabDescriptor("SearchEverywhere.IDE");

  private final String id;

  private SearchEverywhereTabDescriptor(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }
}
