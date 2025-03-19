// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class LocalData extends DescriptorData<LocalVariableDescriptorImpl> {
  private final LocalVariableProxyImpl myLocalVariable;

  public LocalData(LocalVariableProxyImpl localVariable) {
    super();
    myLocalVariable = localVariable;
  }

  @Override
  protected LocalVariableDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new LocalVariableDescriptorImpl(project, myLocalVariable);
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof LocalData)) return false;

    return ((LocalData)object).myLocalVariable.equals(myLocalVariable);
  }

  @Override
  public int hashCode() {
    return myLocalVariable.hashCode();
  }

  @Override
  public DisplayKey<LocalVariableDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<>(myLocalVariable.typeName() + "#" + myLocalVariable.name());
  }
}
