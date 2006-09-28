package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class StackFrameData extends DescriptorData<StackFrameDescriptorImpl>{
  private static final Key STACK_FRAME = new Key("STACK_FRAME");
  private final StackFrameProxyImpl myFrame;

  public StackFrameData(StackFrameProxyImpl frame) {
    super();
    myFrame = frame;
  }

  protected StackFrameDescriptorImpl createDescriptorImpl(Project project) {
    return new StackFrameDescriptorImpl(myFrame);
  }

  public boolean equals(Object object) {
    if(!(object instanceof StackFrameData)) return false;

    return ((StackFrameData)object).myFrame == myFrame;
  }

  public int hashCode() {
    return myFrame.hashCode();
  }

  public DisplayKey<StackFrameDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<StackFrameDescriptorImpl>(STACK_FRAME);
  }

}
