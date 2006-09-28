package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl;
import com.intellij.openapi.project.Project;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class LocalData extends DescriptorData<LocalVariableDescriptorImpl>{
  private final LocalVariableProxyImpl myLocalVariable;

  public LocalData(LocalVariableProxyImpl localVariable) {
    super();
    myLocalVariable = localVariable;
  }

  protected LocalVariableDescriptorImpl createDescriptorImpl(Project project) {
    return new LocalVariableDescriptorImpl(project, myLocalVariable);
  }

  public boolean equals(Object object) {
    if(!(object instanceof LocalData)) return false;

    return ((LocalData)object).myLocalVariable.equals(myLocalVariable);
  }

  public int hashCode() {
    return myLocalVariable.hashCode();
  }

  public DisplayKey<LocalVariableDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<LocalVariableDescriptorImpl>(myLocalVariable);
  }
}
