// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.speedSearch;

import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A renderer which is also aware of search string to filter items in a list.
 */
@Experimental
public interface SearchAwareRenderer<T> extends ListCellRenderer<T> {

  @Nullable
  String getItemSearchString(@NotNull T item);
}
