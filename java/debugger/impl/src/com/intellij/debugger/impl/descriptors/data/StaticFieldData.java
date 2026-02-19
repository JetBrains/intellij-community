// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Field;
import org.jetbrains.annotations.NotNull;

public final class StaticFieldData extends DescriptorData<FieldDescriptorImpl> {
  private final Field myField;

  public StaticFieldData(@NotNull Field field) {
    myField = field;
  }

  @Override
  protected FieldDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new FieldDescriptorImpl(project, null, myField);
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof StaticFieldData fieldData)) {
      return false;
    }
    return (fieldData.myField == myField);
  }

  @Override
  public int hashCode() {
    return myField.hashCode();
  }

  @Override
  public DisplayKey<FieldDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<>(myField);
  }
}
