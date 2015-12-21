/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;

public abstract class NodeDescriptor<E> {
  protected final Project myProject;
  private final NodeDescriptor myParentDescriptor;

  protected String myName;
  protected Icon myClosedIcon;

  /**
   * Unused. It's there only for API compatibility.
   */
  @Deprecated
  protected Icon myOpenIcon;
  protected Color myColor;

  private int myIndex = -1;

  private long myChildrenSortingStamp = -1;
  private long myUpdateCount;

  private boolean myWasDeclaredAlwaysLeaf;

  public NodeDescriptor(@Nullable Project project, @Nullable NodeDescriptor parentDescriptor) {
    myProject = project;
    myParentDescriptor = parentDescriptor;
  }

  @Nullable
  public NodeDescriptor getParentDescriptor() {
    return myParentDescriptor;
  }

  public int getIndex() {
    return myIndex;
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public abstract boolean update();

  public abstract E getElement();

  public String toString() {
    return myName;
  }

  /**
   Use #getIcon() instead
   */
  @Deprecated
  public final Icon getOpenIcon() {
    return getIcon();
  }

  /**
   Use #getIcon() instead
   */
  @Deprecated
  public final Icon getClosedIcon() {
    return getIcon();
  }

  public final Icon getIcon() {
    return myClosedIcon;
  }

  public final Color getColor() {
    return myColor;
  }

  @Nullable
  public final Project getProject() {
    return myProject;
  }

  public boolean expandOnDoubleClick() {
    return true;
  }

  public int getWeight() {
    E element = getElement();
    if (element instanceof WeighedItem) {
      return ((WeighedItem) element).getWeight();
    }
    return 30;
  }


  public final long getChildrenSortingStamp() {
    return myChildrenSortingStamp;
  }

  public final void setChildrenSortingStamp(long stamp) {
    myChildrenSortingStamp = stamp;
  }

  public final long getUpdateCount() {
    return myUpdateCount;
  }

  public final void setUpdateCount(long updateCount) {
    myUpdateCount = updateCount;
  }

  public boolean isWasDeclaredAlwaysLeaf() {
    return myWasDeclaredAlwaysLeaf;
  }

  public void setWasDeclaredAlwaysLeaf(boolean leaf) {
    myWasDeclaredAlwaysLeaf = leaf;
  }

  public void applyFrom(NodeDescriptor desc) {
    setIcon(desc.getIcon());
    myName = desc.myName;
    myColor = desc.myColor;
  }

  public void setIcon(Icon closedIcon) {
    myClosedIcon = closedIcon;
  }

  public abstract static class NodeComparator<T extends NodeDescriptor> implements Comparator<T> {

    private long myStamp;

    public final void setStamp(long stamp) {
      myStamp = stamp;
    }

    public long getStamp() {
      return myStamp;
    }

    public void incStamp() {
      setStamp(getStamp() + 1);
    }

    public static class Delegate<T extends NodeDescriptor> extends NodeComparator<T> {

      private NodeComparator<T> myDelegate;

      protected Delegate(NodeComparator<T> delegate) {
        myDelegate = delegate;
      }

      public void setDelegate(NodeComparator<T> delegate) {
        myDelegate = delegate;
      }

      @Override
      public long getStamp() {
        return myDelegate.getStamp();
      }

      @Override
      public void incStamp() {
        myDelegate.incStamp();
      }

      @Override
      public int compare(T o1, T o2) {
        return myDelegate.compare(o1, o2);
      }
    }
  }
}
