/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.MethodsTracker;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

public class StackFrameData extends DescriptorData<StackFrameDescriptorImpl>{
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

  public boolean equals(Object object) {
    if (!(object instanceof StackFrameData)) {
      return false;
    }
    return ((StackFrameData)object).myFrame == myFrame;
  }

  public int hashCode() {
    return myFrame.hashCode();
  }

  @Override
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
