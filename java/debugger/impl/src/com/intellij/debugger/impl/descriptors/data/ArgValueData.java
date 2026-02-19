// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.jdi.DecompiledLocalVariable;
import com.intellij.debugger.ui.impl.watch.ArgumentValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

public class ArgValueData extends DescriptorData<ArgumentValueDescriptorImpl> {
  private final DecompiledLocalVariable myVariable;
  private final Value myValue;

  public ArgValueData(DecompiledLocalVariable variable, Value value) {
    myVariable = variable;
    myValue = value;
  }

  @Override
  protected ArgumentValueDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new ArgumentValueDescriptorImpl(project, myVariable, myValue);
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof ArgValueData)) return false;

    return myVariable.slot() == ((ArgValueData)object).myVariable.slot();
  }

  @Override
  public int hashCode() {
    return myVariable.slot();
  }

  @Override
  public DisplayKey<ArgumentValueDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<>(myVariable.slot());
  }
}