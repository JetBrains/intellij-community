// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl.exclude;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

abstract class ExcludeListItem {
  public static final Comparator<ExcludeListItem> COMPARATOR =
    (o1, o2) -> StringUtil.comparePairs(o1.getPresentableFrameworkName(), o1.getFileUrl(), o2.getPresentableFrameworkName(), o2.getFileUrl(), true);

  public abstract void renderItem(ColoredListCellRenderer renderer);

  public abstract @Nullable String getPresentableFrameworkName();

  public abstract @Nullable String getFrameworkTypeId();

  public abstract @Nullable String getFileUrl();
}
