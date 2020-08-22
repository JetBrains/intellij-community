// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.openapi.util.ClassExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public final class ItemPresentationProviders extends ClassExtension<ItemPresentationProvider> {
  public static final ItemPresentationProviders INSTANCE = new ItemPresentationProviders();

  private ItemPresentationProviders() {
    super("com.intellij.itemPresentationProvider");
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
  public static <T extends NavigationItem> ItemPresentationProvider<T> getItemPresentationProvider(@NotNull T element) {
    return (ItemPresentationProvider<T>)INSTANCE.forClass(element.getClass());
  }

  @Nullable
  public static ItemPresentation getItemPresentation(@NotNull NavigationItem element) {
    final ItemPresentationProvider<NavigationItem> provider = getItemPresentationProvider(element);
    return provider == null ? null : provider.getPresentation(element);
  }
}
