// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ThreadData extends DescriptorData<ThreadDescriptorImpl> {
  private final ThreadReferenceProxyImpl myThread;

  public ThreadData(ThreadReferenceProxyImpl thread) {
    super();
    myThread = thread;
  }

  @Override
  protected ThreadDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new ThreadDescriptorImpl(myThread);
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof ThreadData)) {
      return false;
    }
    return myThread.equals(((ThreadData)object).myThread);
  }

  @Override
  public int hashCode() {
    return myThread.hashCode();
  }

  @Override
  public DisplayKey<ThreadDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<>(myThread);
  }
}
