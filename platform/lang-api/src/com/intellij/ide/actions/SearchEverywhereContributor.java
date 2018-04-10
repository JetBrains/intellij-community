/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.actions;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
public interface SearchEverywhereContributor {
  ExtensionPointName<SearchEverywhereContributor> EP_NAME = ExtensionPointName.create("com.intellij.searchEverywhereContributor");
  @NotNull
  String getSearchProviderId();

  @NotNull
  String getGroupName();

  int getSortWeight();

  static List<SearchEverywhereContributor> getProvidersSorted() {
    return Arrays.stream(EP_NAME.getExtensions())
      .sorted(Comparator.comparingInt(SearchEverywhereContributor::getSortWeight))
      .collect(Collectors.toList());
  }
}
