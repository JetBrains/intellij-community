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

import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Dmitry Batkovich
 */
public abstract class CachedInspectionTreeNode extends InspectionTreeNode implements RefElementAware {
  private final AtomicReference<String> myPresentableName = new AtomicReference<>();
  private final AtomicReference<Boolean> myValid = new AtomicReference<>();

  protected CachedInspectionTreeNode(Object userObject) {
    super(userObject);
  }

  @Override
  public final boolean isValid() {
    return myValid.updateAndGet((b) -> {
      if (b == null) {
        b = calculateIsValid();
      }
      return b;
    });
  }

  @Override
  public final String toString() {
    return myPresentableName.updateAndGet((s) -> {
      if (s == null) {
        s = calculatePresentableName();
      }
      return s;
    });
  }

  protected final void init() {
    myPresentableName.set(calculatePresentableName());
    myValid.set(calculateIsValid());
  }

  protected abstract String calculatePresentableName();

  protected abstract boolean calculateIsValid();

  @SuppressWarnings("ResultOfMethodCallIgnored")
  final void dropCache() {
    myValid.set(calculateIsValid());
    myPresentableName.set(calculatePresentableName());
    for (int i = 0; i < getChildCount(); i++) {
      TreeNode child = getChildAt(i);
      if (child instanceof CachedInspectionTreeNode) {
        ((CachedInspectionTreeNode)child).dropCache();
      }
    }
  }

  @Nullable
  @Override
  public String getCustomizedTailText() {
    return isValid() ? null : "No longer valid";
  }
}
