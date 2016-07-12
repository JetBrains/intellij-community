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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DiffErrorElement;
import com.intellij.ide.diff.DiffType;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

import static com.intellij.ide.diff.DiffType.ERROR;

/**
 * @author Konstantin Bulenkov
 */
public class DTree {
  private static final Comparator<DTree> COMPARATOR = (o1, o2) -> {
    final boolean b1 = o1.isContainer();
    final boolean b2 = o2.isContainer();
    return (b1 && b2) || (!b1 && !b2)
           ? o1.getName().compareToIgnoreCase(o2.getName())
           : b1 ? 1 : -1;
  };

  private boolean myExpanded = true;
  @Nullable private final DTree myParent;
  private HashMap<String, DTree> myChildren;
  private String myName;
  private final boolean isContainer;
  private SortedList<DTree> myChildrenList;
  private DiffElement<?> mySource;
  private DiffElement<?> myTarget;
  private DiffType myType;
  private boolean myVisible = true;
  private String mySeparator = null;
  private String myPath = null;

  public DTree(@Nullable DTree parent, @NotNull String name, boolean container) {
    this.myParent = parent;
    this.myName = name;
    isContainer = container;
  }

  @NotNull
  public Collection<DTree> getChildren() {
    init();
    if (myChildrenList == null) {
      myChildrenList = new SortedList<>(COMPARATOR);
      myChildrenList.addAll(myChildren.values());
    }
    return myChildrenList;
  }

  public DTree addChild(@NotNull DiffElement element, boolean source) {
    init();
    myChildrenList = null;
    final DTree node;
    final String name = element.getName();
    if (myChildren.containsKey(name)) {
      node = myChildren.get(name);
    } else {
      node = new DTree(this, name, element.isContainer());
      myChildren.put(name, node);
    }

    if (source) {
      node.setSource(element);
    } else {
      node.setTarget(element);
    }

    return node;
  }

  public DiffElement<?> getSource() {
    return mySource;
  }

  public void setSource(DiffElement<?> source) {
    mySource = source;
  }

  public DiffElement<?> getTarget() {
    return myTarget;
  }

  public void setTarget(DiffElement<?> target) {
    myTarget = target;
  }

  private void init() {
    if (myChildren == null) {
      myChildren = new HashMap<>();
    }
  }

  public String getName() {
    return myName;
  }

  @Nullable
  public DTree getParent() {
    return myParent;
  }

  public boolean isExpanded() {
    return myExpanded;
  }

  public void setExpanded(boolean expanded) {
    this.myExpanded = expanded;
  }

  public boolean isContainer() {
    return isContainer;
  }

  @Override
  public String toString() {
    return myName;
  }

  public void update(DirDiffSettings settings) {
    for (DTree tree : getChildren()) {
      final DiffElement<?> src = tree.getSource();
      final DiffElement<?> trg = tree.getTarget();
      if (src instanceof DiffErrorElement || trg instanceof DiffErrorElement) {
        tree.setType(ERROR);
      } else if (src == null && trg != null) {
        tree.setType(DiffType.TARGET);
      } else if (src != null && trg == null) {
        tree.setType(DiffType.SOURCE);
      } else {
        assert src != null;
        DiffType dtype = src.getSize() == trg.getSize() ? DiffType.EQUAL : DiffType.CHANGED;
        if (dtype == DiffType.EQUAL) {
          switch (settings.compareMode) {
            case CONTENT:
              dtype = isEqual(src, trg) ? DiffType.EQUAL : DiffType.CHANGED;
              break;
            case TIMESTAMP:
              dtype = Math.abs(src.getTimeStamp() - trg.getTimeStamp()) <= settings.compareTimestampAccuracy ? DiffType.EQUAL : DiffType.CHANGED;
              break;
          }
        }
        tree.setType(dtype);
      }
      tree.update(settings);
    }
  }

  public boolean isVisible() {
    return myVisible;
  }

  public void updateVisibility(DirDiffSettings settings) {
    if (getChildren().isEmpty()) {
     if (myType == ERROR) {
        myVisible = true;
       return;
      }
      if (myType != DiffType.SEPARATOR && !"".equals(settings.getFilter())) {
        if (!settings.getFilterPattern().matcher(getName()).matches()) {
          myVisible = false;
          return;
        }
      }
      if (myType == null) {
        myVisible = true;
      } else {
      switch (myType) {
        case SOURCE:
          myVisible = settings.showNewOnSource;
          break;
        case TARGET:
          myVisible = settings.showNewOnTarget;
          break;
        case SEPARATOR:
        case ERROR:
          myVisible = true;
          break;
        case CHANGED:
          myVisible = settings.showDifferent;
          break;
        case EQUAL:
          myVisible = settings.showEqual;
          break;
      }
      }
    } else {
      myVisible = false;
      for (DTree child : myChildren.values()) {
        child.updateVisibility(settings);
        myVisible = myVisible || child.isVisible();
      }
    }
  }

  public void reset() {
    myChildren.clear();
  }

  public void remove(DTree node) {
    init();
    final boolean removed = myChildrenList.remove(node);
    if (removed) {
      for (String key : myChildren.keySet()) {
        if (myChildren.get(key) == node) {
          myChildren.remove(key);
          return;
        }
      }
    }
  }

  private static boolean isEqual(DiffElement file1, DiffElement file2) {
    if (file1.isContainer() || file2.isContainer()) return false;
    if (file1.getSize() != file2.getSize()) return false;
    try {
      return Arrays.equals(file1.getContent(), file2.getContent());
    }
    catch (IOException e) {
      return false;
    }
  }

  public DiffType getType() {
    return myType;
  }

  public void setType(DiffType type) {
    this.myType = type;
  }

  public String getPath() {
    if (myPath == null) {
      final DTree parent = getParent();
      if (parent != null) {
        myPath = parent.getPath() + getName() + (isContainer ? getSeparator() : "");
      } else {
        myPath = getName() + (isContainer ? getSeparator() : "");
      }
    }
    return myPath;
  }

  private String getSeparator() {
    if (mySeparator == null) {
      mySeparator = mySource != null ? mySource.getSeparator() : myTarget != null ? myTarget.getSeparator() : "";
    }
    return mySeparator;
  }
}
