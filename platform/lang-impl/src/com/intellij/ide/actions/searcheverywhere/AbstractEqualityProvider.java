// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public abstract class AbstractEqualityProvider implements SEResultsEqualityProvider {

  @Override
  @ApiStatus.Experimental
  public final @NotNull SEEqualElementsActionType compareItemsCollection(
    @NotNull SearchEverywhereFoundElementInfo newItem,
    @NotNull Collection<? extends SearchEverywhereFoundElementInfo> alreadyFoundItems
  ) {
    return alreadyFoundItems.stream()
      .map(alreadyFoundItem -> {
        if (areEqual(newItem, alreadyFoundItem)) {
          return SearchEverywhereFoundElementInfo.COMPARATOR.compare(newItem, alreadyFoundItem) > 0
                 ? new SEEqualElementsActionType.Replace(alreadyFoundItem)
                 : SEEqualElementsActionType.Skip.INSTANCE;
        }
        return SEEqualElementsActionType.DoNothing.INSTANCE;
      })
      .reduce((foo, bar) -> foo.combine(bar))
      .orElse(SEEqualElementsActionType.DoNothing.INSTANCE);
  }

  @Override
  public final @NotNull SEEqualElementsActionType compareItems(@NotNull SearchEverywhereFoundElementInfo newItem,
                                                         @NotNull List<? extends @NotNull SearchEverywhereFoundElementInfo> alreadyFoundItems) {
    return compareItemsCollection(newItem, alreadyFoundItems);
  }

  protected abstract boolean areEqual(@NotNull SearchEverywhereFoundElementInfo newItem,
                                      @NotNull SearchEverywhereFoundElementInfo alreadyFoundItem);
}
