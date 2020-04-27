// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

public interface KeyedLazyInstance<T> {
  String getKey();

  @NotNull
  T getInstance();
}