// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.tree.NodeDescriptor;

public interface DisplayKey<T extends NodeDescriptor> extends DescriptorKey<T> {
  boolean equals(Object object);

  int hashCode();
}
