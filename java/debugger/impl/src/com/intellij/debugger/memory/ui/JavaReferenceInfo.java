// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.ui;

import com.intellij.debugger.memory.utils.InstanceValueDescriptor;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.memory.ui.ReferenceInfo;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public class JavaReferenceInfo implements ReferenceInfo {
  private final ObjectReference objectReference;

  public JavaReferenceInfo(@NotNull ObjectReference objectReference) {
    this.objectReference = objectReference;
  }

  public ValueDescriptorImpl createDescriptor(@NotNull Project project) {
    return new InstanceValueDescriptor(project, objectReference);
  }

  public @NotNull ObjectReference getObjectReference() {
    return objectReference;
  }

  @Override
  public int hashCode() {
    return objectReference.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof JavaReferenceInfo)) {
      return false;
    }
    return ((JavaReferenceInfo)obj).objectReference.equals(objectReference);
  }
}
