// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ActionsEqualityProvider extends AbstractEqualityProvider {

  @Override
  protected boolean areEqual(@NotNull SearchEverywhereFoundElementInfo newItem,
                             @NotNull SearchEverywhereFoundElementInfo alreadyFoundItem) {
    AnAction newAction = extractAction(newItem.getElement());
    AnAction oldAction = extractAction(alreadyFoundItem.getElement());

    return newAction != null && oldAction != null && newAction.equals(oldAction);
  }

  private static @Nullable AnAction extractAction(Object item) {
    if (item == null) return null;

    if (item instanceof AnAction) return (AnAction)item;

    if (item instanceof GotoActionModel.MatchedValue) {
      item = ((GotoActionModel.MatchedValue)item).value;
    }

    if (item instanceof GotoActionModel.ActionWrapper wrapper) {
      return wrapper.getAction();
    }

    return null;
  }
}
