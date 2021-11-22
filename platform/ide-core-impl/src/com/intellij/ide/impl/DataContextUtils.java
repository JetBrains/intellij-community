// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.openapi.actionSystem.DataContext;

public class DataContextUtils {
  public static boolean isFrozenDataContext(DataContext context) {
    return (context instanceof FreezingDataContext) && ((FreezingDataContext)context).isFrozenDataContext();
  }
}
