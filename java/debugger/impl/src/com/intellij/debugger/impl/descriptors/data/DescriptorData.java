package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public abstract class DescriptorData <T extends NodeDescriptor> implements DescriptorKey<T>{
  private static final Key DESCRIPTOR_DATA = new Key("DESCRIPTOR_DATA");

  protected DescriptorData() {
  }

  public T createDescriptor(Project project) {
    T descriptor = createDescriptorImpl(project);
    descriptor.putUserData(DESCRIPTOR_DATA, this);
    return descriptor;
  }

  protected abstract T createDescriptorImpl(Project project);

  public abstract boolean equals(Object object);

  public abstract int hashCode();

  public abstract DisplayKey<T> getDisplayKey();

  public static <T extends NodeDescriptor> DescriptorData<T> getDescriptorData(T descriptor) {
    return (DescriptorData<T>)descriptor.getUserData(DESCRIPTOR_DATA);
  }
}
