/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ArrayReference;
import org.jetbrains.annotations.NotNull;

public final class ArrayItemData extends DescriptorData<ArrayElementDescriptorImpl>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.descriptors.data.ArrayItemData");

  private final ArrayReference myArray;
  private final int myIndex;

  public ArrayItemData(@NotNull ArrayReference arrRef, int idx) {
    LOG.assertTrue(0 <= idx);
    if(LOG.isDebugEnabled()) {
      LOG.assertTrue(idx <= arrRef.length());
    }
    myArray = arrRef;
    myIndex = idx;
  }

  protected ArrayElementDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new ArrayElementDescriptorImpl(project, myArray, myIndex);
  }

  public DisplayKey<ArrayElementDescriptorImpl> getDisplayKey() {
    return new ArrayItemDisplayKeyImpl(myIndex);
  }

  public boolean equals(Object object) {
    return object instanceof ArrayItemData && myArray.equals(((ArrayItemData)object).myArray) && ((ArrayItemData)object).myIndex == myIndex;
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
      return o instanceof ArrayItemDisplayKeyImpl && ((ArrayItemDisplayKeyImpl)o).myIndex == myIndex;
    }

    public int hashCode() {
      return 0;
    }
  }
}
