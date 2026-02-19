// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.ThisDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public final class ThisData extends DescriptorData<ThisDescriptorImpl> {

  private static final Key THIS = new Key("THIS");

  @Override
  protected ThisDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new ThisDescriptorImpl(project);
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof ThisData)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return THIS.hashCode();
  }

  @Override
  public DisplayKey<ThisDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<>(THIS);
  }
}
