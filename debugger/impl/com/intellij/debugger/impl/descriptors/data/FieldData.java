package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public final class FieldData extends DescriptorData<FieldDescriptorImpl>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.descriptors.data.FieldData");

  private final ObjectReference myObjRef;
  private final Field myField;

  public FieldData(ObjectReference objRef, Field field) {
    super();
    LOG.assertTrue(objRef != null);
    LOG.assertTrue(field != null);
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
    return (fieldData.myField == myField) && (fieldData.myObjRef.equals(myObjRef));
  }

  public int hashCode() {
    return myObjRef.hashCode() + myField.hashCode();
  }

  public DisplayKey<FieldDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<FieldDescriptorImpl>(myField);
  }
}
