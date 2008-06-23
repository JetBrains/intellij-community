package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Field;
import org.jetbrains.annotations.NotNull;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public final class StaticFieldData extends DescriptorData<FieldDescriptorImpl>{
  private final Field myField;

  public StaticFieldData(@NotNull Field field) {
    myField = field;
  }

  protected FieldDescriptorImpl createDescriptorImpl(Project project) {
    return new FieldDescriptorImpl(project, null, myField);
  }

  public boolean equals(Object object) {
    if(!(object instanceof StaticFieldData)) {
      return false;
    }
    final StaticFieldData fieldData = (StaticFieldData)object;
    return (fieldData.myField == myField);
  }

  public int hashCode() {
    return myField.hashCode();
  }

  public DisplayKey<FieldDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<FieldDescriptorImpl>(myField);
  }
}
