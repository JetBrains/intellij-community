// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.StaticDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

public final class StaticData extends DescriptorData<StaticDescriptorImpl> {
  private static final Key STATIC = new Key("STATIC");

  private final ReferenceType myRefType;

  public StaticData(@NotNull ReferenceType refType) {
    myRefType = refType;
  }

  public ReferenceType getRefType() {
    return myRefType;
  }

  @Override
  protected StaticDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new StaticDescriptorImpl(myRefType);
  }

  @Override
  public boolean equals(Object object) {
    return object instanceof StaticData;
  }

  @Override
  public int hashCode() {
    return STATIC.hashCode();
  }

  @Override
  public DisplayKey<StaticDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<>(STATIC);
  }
}
