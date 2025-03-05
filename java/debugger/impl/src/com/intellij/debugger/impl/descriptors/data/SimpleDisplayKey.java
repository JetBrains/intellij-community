// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.tree.NodeDescriptor;

public class SimpleDisplayKey<T extends NodeDescriptor> implements DisplayKey<T> {
  private final Object myKey;

  public SimpleDisplayKey(Object key) {
    myKey = key;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SimpleDisplayKey)) return false;
    return ((SimpleDisplayKey<?>)o).myKey.equals(myKey);
  }

  @Override
  public int hashCode() {
    return myKey.hashCode();
  }
}
