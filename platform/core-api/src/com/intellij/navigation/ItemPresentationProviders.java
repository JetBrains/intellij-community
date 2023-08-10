// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation;

import com.intellij.openapi.util.ClassExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ItemPresentationProviders extends ClassExtension<ItemPresentationProvider> {
  public static final ItemPresentationProviders INSTANCE = new ItemPresentationProviders();

  private ItemPresentationProviders() {
    super("com.intellij.itemPresentationProvider");
  }

  @SuppressWarnings({"unchecked"})
  public static @Nullable ItemPresentation getItemPresentation(@NotNull NavigationItem element) {
    for (ItemPresentationProvider<NavigationItem> provider : INSTANCE.forKey(element.getClass())) {
      ItemPresentation presentation = provider.getPresentation(element);
      if (presentation != null) {
        return presentation;
      }
    }

    return null;
  }
}
