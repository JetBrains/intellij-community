// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.task.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;

@ApiStatus.Internal
@SuppressWarnings("unchecked")
public class ExternalSystemNode<T> extends DefaultMutableTreeNode {

  public ExternalSystemNode(@NotNull ExternalSystemNodeDescriptor<T> descriptor) {
    super(descriptor);
  }

  public @NotNull ExternalSystemNodeDescriptor<T> getDescriptor() {
    return (ExternalSystemNodeDescriptor<T>)getUserObject();
  }

  @Override
  public ExternalSystemNode<?> getChildAt(int index) {
    return (ExternalSystemNode<?>)super.getChildAt(index);
  }
}
