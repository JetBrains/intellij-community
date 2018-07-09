// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class LocalData extends DescriptorData<LocalVariableDescriptorImpl>{
  private final LocalVariableProxyImpl myLocalVariable;

  public LocalData(LocalVariableProxyImpl localVariable) {
    super();
    myLocalVariable = localVariable;
  }

  protected LocalVariableDescriptorImpl createDescriptorImpl(@NotNull Project project) {
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
    return new SimpleDisplayKey<>(myLocalVariable.typeName() + "#" + myLocalVariable.name());
  }
}
