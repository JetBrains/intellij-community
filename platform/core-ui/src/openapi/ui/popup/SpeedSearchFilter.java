// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

public interface SpeedSearchFilter<T> {
  default boolean canBeHidden(T value) {
    return true;
  }

  String getIndexedString(T value);
}
