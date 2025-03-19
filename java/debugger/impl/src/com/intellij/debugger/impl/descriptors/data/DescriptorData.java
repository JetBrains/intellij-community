// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public abstract class DescriptorData<T extends NodeDescriptor> implements DescriptorKey<T> {
  private static final Key DESCRIPTOR_DATA = new Key("DESCRIPTOR_DATA");

  protected DescriptorData() {
  }

  public T createDescriptor(@NotNull Project project) {
    T descriptor = createDescriptorImpl(project);
    descriptor.putUserData(DESCRIPTOR_DATA, this);
    return descriptor;
  }

  protected abstract T createDescriptorImpl(@NotNull Project project);

  @Override
  public abstract boolean equals(Object object);

  @Override
  public abstract int hashCode();

  public abstract DisplayKey<T> getDisplayKey();

  public static <T extends NodeDescriptor> DescriptorData<T> getDescriptorData(T descriptor) {
    return (DescriptorData<T>)descriptor.getUserData(DESCRIPTOR_DATA);
  }
}
