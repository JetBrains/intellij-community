package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.ThisDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.sun.jdi.Value;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public final class ThisData extends DescriptorData<ThisDescriptorImpl>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.descriptors.data.ThisData");

  private static final Key THIS = new Key("THIS");

  private final Value myThisValue;

  public ThisData(Value thisValue) {
    super();
    LOG.assertTrue(thisValue != null);
    myThisValue = thisValue;
  }

  public Value getThisValue() {
    return myThisValue;
  }

  protected ThisDescriptorImpl createDescriptorImpl(Project project) {
    return new ThisDescriptorImpl(project, myThisValue);
  }

  public boolean equals(Object object) {
    if(!(object instanceof ThisData)) return false;

    return true;
  }

  public int hashCode() {
    return THIS.hashCode();
  }

  public DisplayKey<ThisDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<ThisDescriptorImpl>(THIS);
  }
}
