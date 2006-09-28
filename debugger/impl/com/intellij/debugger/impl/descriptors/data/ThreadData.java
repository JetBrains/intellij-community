package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.openapi.project.Project;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ThreadData extends DescriptorData<ThreadDescriptorImpl> {
  private final ThreadReferenceProxyImpl myThread;
  public ThreadData(ThreadReferenceProxyImpl thread) {
    super();
    myThread = thread;
  }

  protected ThreadDescriptorImpl createDescriptorImpl(Project project) {
    return new ThreadDescriptorImpl(myThread);
  }

  public boolean equals(Object object) {
    if(!(object instanceof ThreadData)) return false;

    return ((ThreadData)object).myThread == myThread;
  }

  public int hashCode() {
    return myThread.hashCode();
  }

  public DisplayKey<ThreadDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<ThreadDescriptorImpl>(myThread);
  }
}
