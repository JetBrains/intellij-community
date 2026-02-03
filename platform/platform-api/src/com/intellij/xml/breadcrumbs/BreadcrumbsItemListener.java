// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.breadcrumbs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BreadcrumbsItemListener<T extends BreadcrumbsItem> {
  void itemSelected(final @NotNull T item, final int modifiers);
  void itemHovered(final @Nullable T item);
}
