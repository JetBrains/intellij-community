// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public final class FieldData extends DescriptorData<FieldDescriptorImpl> {
  private final ObjectReference myObjRef;
  private final Field myField;

  public FieldData(@NotNull ObjectReference objRef, @NotNull Field field) {
    myObjRef = objRef;
    myField = field;
  }

  @Override
  protected FieldDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new FieldDescriptorImpl(project, myObjRef, myField);
  }

  @Override
  public boolean equals(Object object) {
    return object instanceof FieldData fieldData && fieldData.myField == myField && fieldData.myObjRef.equals(myObjRef);
  }

  @Override
  public int hashCode() {
    return myObjRef.hashCode() + myField.hashCode();
  }

  @Override
  public DisplayKey<FieldDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<>(myField);
  }
}
