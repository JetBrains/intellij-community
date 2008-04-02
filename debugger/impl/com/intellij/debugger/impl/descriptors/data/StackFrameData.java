package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.MethodsTracker;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class StackFrameData extends DescriptorData<StackFrameDescriptorImpl>{
  private final StackFrameProxyImpl myFrame;
  private final FrameDisplayKey myDisplayKey;
  private MethodsTracker myMethodsTracker;

  public StackFrameData(StackFrameProxyImpl frame) {
    super();
    myFrame = frame;
    myDisplayKey = new FrameDisplayKey(NodeManagerImpl.getContextKey(frame));
    myMethodsTracker = new MethodsTracker();
    
  }

  protected StackFrameDescriptorImpl createDescriptorImpl(Project project) {
    return new StackFrameDescriptorImpl(myFrame, myMethodsTracker);
  }

  public boolean equals(Object object) {
    if(!(object instanceof StackFrameData)) return false;

    return ((StackFrameData)object).myFrame == myFrame;
  }

  public int hashCode() {
    return myFrame.hashCode();
  }

  public DisplayKey<StackFrameDescriptorImpl> getDisplayKey() {
    return myDisplayKey;
  }

  private static class FrameDisplayKey implements DisplayKey<StackFrameDescriptorImpl>{
    private final String myContextKey;

    public FrameDisplayKey(final String contextKey) {
      myContextKey = contextKey;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final FrameDisplayKey that = (FrameDisplayKey)o;

      if (!Comparing.equal(myContextKey, that.myContextKey)) return false;
      
      return true;
    }

    public int hashCode() {
      return myContextKey == null? 0 : myContextKey.hashCode();
    }
  } 
  
}
