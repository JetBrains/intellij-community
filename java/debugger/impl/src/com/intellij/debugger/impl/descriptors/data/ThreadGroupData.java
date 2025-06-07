// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.ThreadGroupDescriptorImpl;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ThreadGroupData extends DescriptorData<ThreadGroupDescriptorImpl> {
  private final ThreadGroupReferenceProxyImpl myThreadGroup;

  public ThreadGroupData(ThreadGroupReferenceProxyImpl threadGroup) {
    super();
    myThreadGroup = threadGroup;
  }

  @Override
  protected ThreadGroupDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new ThreadGroupDescriptorImpl(myThreadGroup);
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof ThreadGroupData)) return false;

    return myThreadGroup.equals(((ThreadGroupData)object).myThreadGroup);
  }

  @Override
  public int hashCode() {
    return myThreadGroup.hashCode();
  }

  @Override
  public DisplayKey<ThreadGroupDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<>(myThreadGroup);
  }
}
