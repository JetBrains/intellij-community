package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.StaticDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.sun.jdi.ReferenceType;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public final class StaticData extends DescriptorData<StaticDescriptorImpl>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.descriptors.data.StaticData");

  private static final Key STATIC = new Key("STATIC");

  private final ReferenceType myRefType;

  public StaticData(ReferenceType refType) {
    super();
    LOG.assertTrue(refType != null);
    myRefType = refType;
  }

  public ReferenceType getRefType() {
    return myRefType;
  }

  protected StaticDescriptorImpl createDescriptorImpl(Project project) {
    return new StaticDescriptorImpl(myRefType);
  }

  public boolean equals(Object object) {
    if(!(object instanceof StaticData)) return false;

    return true;
  }

  public int hashCode() {
    return STATIC.hashCode();
  }

  public DisplayKey<StaticDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<StaticDescriptorImpl>(STATIC);
  }
}
