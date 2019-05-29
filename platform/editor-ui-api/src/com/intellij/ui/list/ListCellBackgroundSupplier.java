// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.list;

import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface ListCellBackgroundSupplier<T> {
  @Nullable
  Color getCellBackground(T value, int row);
}
