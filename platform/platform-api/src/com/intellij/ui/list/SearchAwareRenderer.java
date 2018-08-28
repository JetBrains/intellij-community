// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.list;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface SearchAwareRenderer<T> extends ListCellRenderer<T> {

  @Nullable
  String getItemName(@NotNull T item);
}
