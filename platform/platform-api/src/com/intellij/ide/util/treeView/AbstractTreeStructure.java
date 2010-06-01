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

package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractTreeStructure {
  public abstract Object getRootElement();
  public abstract Object[] getChildElements(Object element);
  @Nullable
  public abstract Object getParentElement(Object element);

  @NotNull
  public abstract NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor);

  public abstract void commit();
  public abstract boolean hasSomethingToCommit();

  public boolean isToBuildChildrenInBackground(Object element){
    return false;
  }

  public boolean isAlwaysLeaf(Object element) {
    return false;
  }

  public AsyncResult<Object> revalidateElement(Object element) {
    return new AsyncResult.Done<Object>(element);
  }

  public static class Delegate extends AbstractTreeStructure {
    private AbstractTreeStructure myDelegee;

    public Delegate(AbstractTreeStructure delegee) {
      myDelegee = delegee;
    }

    @Override
    public Object getRootElement() {
      return myDelegee.getRootElement();
    }

    @Override
    public Object[] getChildElements(Object element) {
      return myDelegee.getChildElements(element);
    }

    @Override
    public Object getParentElement(Object element) {
      return myDelegee.getParentElement(element);
    }

    @NotNull
    @Override
    public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
      return myDelegee.createDescriptor(element, parentDescriptor);
    }

    @Override
    public void commit() {
      myDelegee.commit();
    }

    @Override
    public boolean hasSomethingToCommit() {
      return myDelegee.hasSomethingToCommit();
    }

    @Override
    public boolean isToBuildChildrenInBackground(Object element) {
      return myDelegee.isToBuildChildrenInBackground(element);
    }

    @Override
    public boolean isAlwaysLeaf(Object element) {
      return myDelegee.isAlwaysLeaf(element);
    }

    @Override
    public AsyncResult revalidateElement(Object element) {
      return myDelegee.revalidateElement(element);
    }
  }

}