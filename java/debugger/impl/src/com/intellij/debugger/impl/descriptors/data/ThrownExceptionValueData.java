// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.ThrownExceptionValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public final class ThrownExceptionValueData extends DescriptorData<ThrownExceptionValueDescriptorImpl> {
  private final @NotNull ObjectReference myExceptionObj;

  public ThrownExceptionValueData(@NotNull ObjectReference exceptionObj) {
    myExceptionObj = exceptionObj;
  }

  @Override
  protected ThrownExceptionValueDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new ThrownExceptionValueDescriptorImpl(project, myExceptionObj);
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ThrownExceptionValueData data = (ThrownExceptionValueData)o;

    if (!myExceptionObj.equals(data.myExceptionObj)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myExceptionObj.hashCode();
  }

  @Override
  public DisplayKey<ThrownExceptionValueDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<>(myExceptionObj);
  }
}
