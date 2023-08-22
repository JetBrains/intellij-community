// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.util.NlsContexts.ColumnName;

public abstract class ImmutableColumnInfo<Item, Aspect> extends ColumnInfo<Item, Aspect> {
  public ImmutableColumnInfo(@ColumnName String name) {
    super(name);
  }

  public abstract Item withValue(Item item, Aspect value);
}
