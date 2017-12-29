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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
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
  private final String myName;
  private final boolean isContainer;
  private SortedList<DTree> myChildrenList;
  private DiffElement<?> mySource;
  private DiffElement<?> myTarget;
  private DiffType myType;
  private boolean myVisible = true;
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

  public DTree addChild(@NotNull DiffElement element, boolean source, String replacementName) {
    init();
    myChildrenList = null;
    final DTree node;
    final String name = element.getName();
    if (replacementName != null && myChildren.containsKey(replacementName)) {
      node = myChildren.get(replacementName);
    }
    else if (myChildren.containsKey(name)) {
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
        boolean equals;
        switch (settings.compareMode) {
          case CONTENT:
            equals = isEqualContents(src, trg);
            break;
          case TEXT:
            equals = isEqualContentsAsText(src, trg);
            break;
          case SIZE:
            equals = isEqualSizes(src, trg);
            break;
          case TIMESTAMP:
            equals = isEqualTimestamps(src, trg, settings);
            break;
          default:
            throw new IllegalStateException(settings.compareMode.name());
        }
        tree.setType(equals ? DiffType.EQUAL : DiffType.CHANGED);
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

  private static boolean isEqualSizes(DiffElement<?> file1, DiffElement<?> file2) {
    return file1.getSize() == file2.getSize();
  }

  private static boolean isEqualTimestamps(DiffElement<?> src, DiffElement<?> trg, DirDiffSettings settings) {
    if (src.getSize() != trg.getSize()) return false;
    return Math.abs(src.getTimeStamp() - trg.getTimeStamp()) <= settings.compareTimestampAccuracy;
  }

  private static boolean isEqualContents(DiffElement<?> file1, DiffElement<?> file2) {
    if (file1.isContainer() || file2.isContainer()) return false;
    if (file1.getSize() != file2.getSize()) return false;
    try {
      return Arrays.equals(file1.getContent(), file2.getContent());
    }
    catch (IOException e) {
      return false;
    }
  }

  private static boolean isEqualContentsAsText(DiffElement<?> file1, DiffElement<?> file2) {
    if (file1.isContainer() || file2.isContainer()) return false;

    if (file1.getFileType().isBinary() || file2.getFileType().isBinary()) {
      return isEqualContents(file1, file2);
    }

    try {
      byte[] content1 = file1.getContent();
      byte[] content2 = file2.getContent();

      if (Arrays.equals(content1, content2)) return true;
      if (content1 == null || content2 == null) return false;

      String text1 = CharsetToolkit.tryDecodeString(content1, file1.getCharset());
      if (text1 == null) return false;
      String text2 = CharsetToolkit.tryDecodeString(content2, file2.getCharset());
      if (text2 == null) return false;

      String convertedText1 = StringUtil.convertLineSeparators(text1);
      String convertedText2 = StringUtil.convertLineSeparators(text2);
      return StringUtil.equals(convertedText1, convertedText2);
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
        myPath = parent.getPath() + getName() + (isContainer ? DiffElement.getSeparator() : "");
      } else {
        myPath = getName() + (isContainer ? DiffElement.getSeparator() : "");
      }
    }
    return myPath;
  }
}
