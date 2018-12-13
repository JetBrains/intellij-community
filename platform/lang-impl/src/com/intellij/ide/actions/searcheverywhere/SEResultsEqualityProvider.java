// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public interface SEResultsEqualityProvider {

  ExtensionPointName<SEResultsEqualityProvider> EP_NAME = ExtensionPointName.create("com.intellij.searchEverywhereResultsEqualityProvider");

  enum Action {
    DO_NOTHING, SKIP, REPLACE
  }

  @NotNull
  Action compareItems(@NotNull SESearcher.ElementInfo newItem, @NotNull SESearcher.ElementInfo alreadyFoundItem);

  @NotNull
  static List<SEResultsEqualityProvider> getProviders() {
    return Arrays.asList(EP_NAME.getExtensions());
  }

  @NotNull
  static SEResultsEqualityProvider composite(@NotNull Collection<? extends SEResultsEqualityProvider> providers) {
    return new SEResultsEqualityProvider() {
      @NotNull
      @Override
      public Action compareItems(@NotNull SESearcher.ElementInfo newItem, @NotNull SESearcher.ElementInfo alreadyFoundItem) {
        return providers.stream()
          .map(provider -> provider.compareItems(newItem, alreadyFoundItem))
          .filter(action -> action != SEResultsEqualityProvider.Action.DO_NOTHING)
          .findFirst()
          .orElse(SEResultsEqualityProvider.Action.DO_NOTHING);
      }
    };
  }
}
