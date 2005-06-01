/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.Nullable;

public interface DataProvider {
  @Nullable
  Object getData(String dataId);
}