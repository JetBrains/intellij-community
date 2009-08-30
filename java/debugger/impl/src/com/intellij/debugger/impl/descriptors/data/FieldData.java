package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public final class FieldData extends DescriptorData<FieldDescriptorImpl>{
  private final ObjectReference myObjRef;
  private final Field myField;

  public FieldData(@NotNull ObjectReference objRef, @NotNull Field field) {
    myObjRef = objRef;
    myField = field;
  }

  protected FieldDescriptorImpl createDescriptorImpl(Project project) {
    return new FieldDescriptorImpl(project, myObjRef, myField);
  }

  public boolean equals(Object object) {
    if(!(object instanceof FieldData)) {
      return false;
    }
    final FieldData fieldData = (FieldData)object;
    return fieldData.myField == myField && fieldData.myObjRef.equals(myObjRef);
  }

  public int hashCode() {
    return myObjRef.hashCode() + myField.hashCode();
  }

  public DisplayKey<FieldDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<FieldDescriptorImpl>(myField);
  }
}
