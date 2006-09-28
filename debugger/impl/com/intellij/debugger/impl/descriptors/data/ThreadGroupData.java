package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.ThreadGroupDescriptorImpl;
import com.intellij.openapi.project.Project;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ThreadGroupData extends DescriptorData<ThreadGroupDescriptorImpl>{
  private final ThreadGroupReferenceProxyImpl myThreadGroup;

  public ThreadGroupData(ThreadGroupReferenceProxyImpl threadGroup) {
    super();
    myThreadGroup = threadGroup;
  }

  protected ThreadGroupDescriptorImpl createDescriptorImpl(Project project) {
    return new ThreadGroupDescriptorImpl(myThreadGroup);
  }

  public boolean equals(Object object) {
    if(!(object instanceof ThreadGroupData)) return false;

    return ((ThreadGroupData)object).myThreadGroup == myThreadGroup;
  }

  public int hashCode() {
    return myThreadGroup.hashCode();
  }

  public DisplayKey<ThreadGroupDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<ThreadGroupDescriptorImpl>(myThreadGroup);
  }
}
