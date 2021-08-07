// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import org.jetbrains.annotations.NotNull;


public interface ItemPresentationProvider<T extends NavigationItem> {
  ItemPresentation getPresentation(@NotNull T item);
}
