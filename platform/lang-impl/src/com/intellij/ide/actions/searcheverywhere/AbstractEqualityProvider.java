// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public abstract class AbstractEqualityProvider implements SEResultsEqualityProvider {

  @Override
  public final @NotNull SEEqualElementsActionType compareItems(
    @NotNull SearchEverywhereFoundElementInfo newItem,
    @NotNull List<? extends SearchEverywhereFoundElementInfo> alreadyFoundItems
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

  protected abstract boolean areEqual(@NotNull SearchEverywhereFoundElementInfo newItem,
                                      @NotNull SearchEverywhereFoundElementInfo alreadyFoundItem);
}
