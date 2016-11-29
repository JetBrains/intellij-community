/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;

/**
 * @author Dmitry Batkovich
 */
public abstract class CachedInspectionTreeNode extends InspectionTreeNode implements RefElementAndDescriptorAware {
  private volatile String myPresentableName;
  private volatile Boolean myValid;

  protected CachedInspectionTreeNode(Object userObject) {
    super(userObject);
  }

  @Override
  public final boolean isValid() {
    Boolean valid = myValid;
    if (valid != null) return valid;
    synchronized (this) {
      valid = myValid;
      if (valid == null) {
        valid = calculateIsValid();
        myValid = valid;
      }
      return valid;
    }
  }

  @Override
  public final String toString() {
    String name = myPresentableName;
    if (name != null) return name;
    synchronized (this) {
      name = myPresentableName;
      if (name == null) {
        name = calculatePresentableName();
        myPresentableName = name;
      }
      return name;
    }
  }

  protected void init(Project project) {
    myPresentableName = calculatePresentableName();
    myValid = calculateIsValid();
  }

  protected abstract String calculatePresentableName();

  protected abstract boolean calculateIsValid();

  @SuppressWarnings("ResultOfMethodCallIgnored")
  protected void dropCache(Project project) {
    myValid = calculateIsValid();
    myPresentableName = calculatePresentableName();
    for (int i = 0; i < getChildCount(); i++) {
      TreeNode child = getChildAt(i);
      if (child instanceof CachedInspectionTreeNode) {
        ((CachedInspectionTreeNode)child).dropCache(project);
      }
    }
  }

  @Nullable
  @Override
  public String getCustomizedTailText() {
    return isValid() ? null : "No longer valid";
  }
}
