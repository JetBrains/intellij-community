// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement this interface and register the implementation as {@code com.intellij.itemPresentationProvider} extension in plugin.xml
 * to provide presentation of navigation items.
 */
public interface ItemPresentationProvider<T extends NavigationItem> {

  /**
   * @return presentation of the given {@code item},
   * or {@code null} if this provider is not applicable
   */
  @Nullable ItemPresentation getPresentation(@NotNull T item);
}
