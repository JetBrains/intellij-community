// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.MethodsTracker;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class StackFrameData extends DescriptorData<StackFrameDescriptorImpl> {
  private final StackFrameProxyImpl myFrame;
  private final FrameDisplayKey myDisplayKey;
  private final MethodsTracker myMethodsTracker;

  public StackFrameData(@NotNull StackFrameProxyImpl frame) {
    super();

    myFrame = frame;
    myDisplayKey = new FrameDisplayKey(NodeManagerImpl.getContextKeyForFrame(frame));
    myMethodsTracker = new MethodsTracker();
  }

  @Override
  protected StackFrameDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new StackFrameDescriptorImpl(myFrame, myMethodsTracker);
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof StackFrameData)) {
      return false;
    }
    return ((StackFrameData)object).myFrame == myFrame;
  }

  @Override
  public int hashCode() {
    return myFrame.hashCode();
  }

  @Override
  public DisplayKey<StackFrameDescriptorImpl> getDisplayKey() {
    return myDisplayKey;
  }

  private static class FrameDisplayKey implements DisplayKey<StackFrameDescriptorImpl> {
    private final String myContextKey;

    FrameDisplayKey(final String contextKey) {
      myContextKey = contextKey;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final FrameDisplayKey that = (FrameDisplayKey)o;

      if (!Objects.equals(myContextKey, that.myContextKey)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myContextKey == null ? 0 : myContextKey.hashCode();
    }
  }
}
