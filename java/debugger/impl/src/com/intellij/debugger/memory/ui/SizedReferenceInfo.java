// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.ui;

import com.intellij.debugger.memory.utils.InstanceValueDescriptor;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public class SizedReferenceInfo extends JavaReferenceInfo {
  private final long mySize;

  public SizedReferenceInfo(@NotNull ObjectReference objectReference, long size) {
    super(objectReference);
    mySize = size;
  }

  @Override
  public ValueDescriptorImpl createDescriptor(@NotNull Project project) {
    return new SizedValueDescriptor(project, getObjectReference());
  }

  public long size() {
    return mySize;
  }

  private class SizedValueDescriptor extends InstanceValueDescriptor {
    protected SizedValueDescriptor(@NotNull Project project, @NotNull ObjectReference value) {
      super(project, value);
    }

    @Override
    public String calcValueName() {
      return " [" + mySize + "] " + super.calcValueName();
    }
  }
}
