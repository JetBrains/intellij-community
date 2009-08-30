package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.ThisDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public final class ThisData extends DescriptorData<ThisDescriptorImpl>{

  private static final Key THIS = new Key("THIS");

  protected ThisDescriptorImpl createDescriptorImpl(Project project) {
    return new ThisDescriptorImpl(project);
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
