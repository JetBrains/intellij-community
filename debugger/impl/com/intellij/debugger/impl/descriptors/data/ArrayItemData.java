package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ArrayReference;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public final class ArrayItemData extends DescriptorData<ArrayElementDescriptorImpl>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.descriptors.data.ArrayItemData");

  private ArrayReference myArray;
  private int myIndex;

  public ArrayItemData(ArrayReference arrRef, int idx) {
    super();
    LOG.assertTrue(arrRef != null);
    LOG.assertTrue(0 <= idx);
    if(LOG.isDebugEnabled()) {
      LOG.assertTrue(idx <= arrRef.length());
    }
    myArray = arrRef;
    myIndex = idx;
  }

  protected ArrayElementDescriptorImpl createDescriptorImpl(Project project) {
    return new ArrayElementDescriptorImpl(project, myArray, myIndex);
  }

  public DisplayKey<ArrayElementDescriptorImpl> getDisplayKey() {
    return new ArrayItemDisplayKeyImpl(myIndex);
  }

  public boolean equals(Object object) {
    if(!(object instanceof ArrayItemData)) return false;
    return myArray.equals(((ArrayItemData)object).myArray) &&
           ((ArrayItemData)object).myIndex == myIndex;
  }

  public int hashCode() {
    return myArray.hashCode() + myIndex;
  }

  private static class ArrayItemDisplayKeyImpl implements DisplayKey<ArrayElementDescriptorImpl> {
    private final int myIndex;

    public ArrayItemDisplayKeyImpl(int index) {
      myIndex = index;
    }

    public boolean equals(Object o) {
      if(!(o instanceof ArrayItemDisplayKeyImpl)) return false;
      return ((ArrayItemDisplayKeyImpl)o).myIndex == myIndex;
    }

    public int hashCode() {
      return 0;
    }
  }
}
