// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface MnemonicNavigationFilter<T> {

  int getMnemonicPos(T value);

  String getTextFor(T value);

  @NotNull
  List<T> getValues();
}
