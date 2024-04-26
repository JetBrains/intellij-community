// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.ui;

import com.intellij.debugger.memory.utils.InstanceValueDescriptor;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public class SizedReferenceInfo extends JavaReferenceInfo {
  private final long myRetainedSize;
  private final long myShallowSize;

  public SizedReferenceInfo(@NotNull ObjectReference objectReference, long shallowSize, long retainedSize) {
    super(objectReference);
    myRetainedSize = retainedSize;
    myShallowSize = shallowSize;
  }

  public long getRetainedSize() {
    return myRetainedSize;
  }

  @Override
  public ValueDescriptorImpl createDescriptor(@NotNull Project project) {
    return new SizedValueDescriptor(project, getObjectReference(), myShallowSize, myRetainedSize);
  }

  public static class SizedValueDescriptor extends InstanceValueDescriptor {
    private final long myRetainedSize;
    private final long myShallowSize;

    protected SizedValueDescriptor(@NotNull Project project, @NotNull ObjectReference value, long shallowSize, long retainedSize) {
      super(project, value);
      myRetainedSize = retainedSize;
      myShallowSize = shallowSize;
    }

    public long getRetainedSize() {
      return myRetainedSize;
    }

    public long getShallowSize() {
      return myShallowSize;
    }
  }
}
