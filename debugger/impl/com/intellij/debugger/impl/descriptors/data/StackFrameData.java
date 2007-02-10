package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.MethodsTracker;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.project.Project;

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
    myDisplayKey = new FrameDisplayKey(frame.getIndexFromBottom());
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
    private final int myFrameIndex;
    
    public FrameDisplayKey(final int frameIndex) {
      myFrameIndex = frameIndex;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final FrameDisplayKey that = (FrameDisplayKey)o;

      if (myFrameIndex != that.myFrameIndex) return false;

      return true;
    }

    public int hashCode() {
      return myFrameIndex;
    }
  } 
  
}
