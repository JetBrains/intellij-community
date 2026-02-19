// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ApiStatus.Internal
public final class TrivialElementsEqualityProvider extends AbstractEqualityProvider {

  @Override
  protected boolean areEqual(@NotNull SearchEverywhereFoundElementInfo newItem,
                             @NotNull SearchEverywhereFoundElementInfo alreadyFoundItem) {
    return Objects.equals(newItem.getElement(), alreadyFoundItem.getElement());
  }
}
