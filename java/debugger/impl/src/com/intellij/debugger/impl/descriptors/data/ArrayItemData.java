// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ArrayReference;
import org.jetbrains.annotations.NotNull;

public final class ArrayItemData extends DescriptorData<ArrayElementDescriptorImpl> {
  private static final Logger LOG = Logger.getInstance(ArrayItemData.class);

  private final ArrayReference myArray;
  private final int myIndex;

  public ArrayItemData(@NotNull ArrayReference arrRef, int idx) {
    LOG.assertTrue(0 <= idx);
    if (LOG.isDebugEnabled()) {
      LOG.assertTrue(idx <= arrRef.length());
    }
    myArray = arrRef;
    myIndex = idx;
  }

  @Override
  protected ArrayElementDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new ArrayElementDescriptorImpl(project, myArray, myIndex);
  }

  @Override
  public DisplayKey<ArrayElementDescriptorImpl> getDisplayKey() {
    return new ArrayItemDisplayKeyImpl(myIndex);
  }

  @Override
  public boolean equals(Object object) {
    return object instanceof ArrayItemData && myArray.equals(((ArrayItemData)object).myArray) && ((ArrayItemData)object).myIndex == myIndex;
  }

  @Override
  public int hashCode() {
    return myArray.hashCode() + myIndex;
  }

  private static class ArrayItemDisplayKeyImpl implements DisplayKey<ArrayElementDescriptorImpl> {
    private final int myIndex;

    ArrayItemDisplayKeyImpl(int index) {
      myIndex = index;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof ArrayItemDisplayKeyImpl && ((ArrayItemDisplayKeyImpl)o).myIndex == myIndex;
    }

    @Override
    public int hashCode() {
      return 0;
    }
  }
}
