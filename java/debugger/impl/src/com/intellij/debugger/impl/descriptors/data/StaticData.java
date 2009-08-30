package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.StaticDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public final class StaticData extends DescriptorData<StaticDescriptorImpl>{
  private static final Key STATIC = new Key("STATIC");

  private final ReferenceType myRefType;

  public StaticData(@NotNull ReferenceType refType) {
    myRefType = refType;
  }

  public ReferenceType getRefType() {
    return myRefType;
  }

  protected StaticDescriptorImpl createDescriptorImpl(Project project) {
    return new StaticDescriptorImpl(myRefType);
  }

  public boolean equals(Object object) {
    return object instanceof StaticData;

  }

  public int hashCode() {
    return STATIC.hashCode();
  }

  public DisplayKey<StaticDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<StaticDescriptorImpl>(STATIC);
  }
}
