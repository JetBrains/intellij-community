// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import org.jetbrains.annotations.NotNull;

public class OptionEqualityProvider extends AbstractEqualityProvider {
  @Override
  protected boolean areEqual(@NotNull SearchEverywhereFoundElementInfo newItem,
                             @NotNull SearchEverywhereFoundElementInfo alreadyFoundItem) {
    OptionDescription newOption = extractOption(newItem.getElement());
    OptionDescription oldOption = extractOption(alreadyFoundItem.getElement());

    return newOption != null && oldOption != null && newOption.equals(oldOption);
  }

  private static OptionDescription extractOption(Object item) {
    if (item == null) return null;

    if (item instanceof GotoActionModel.MatchedValue) {
      item = ((GotoActionModel.MatchedValue)item).value;
    }

    return item instanceof OptionDescription ? (OptionDescription)item : null;
  }
}
