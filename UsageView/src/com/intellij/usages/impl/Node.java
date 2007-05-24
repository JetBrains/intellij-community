/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.usages.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * @author max
 */
abstract class Node extends DefaultMutableTreeNode {
  private boolean myIsValid = true;
  protected final DefaultTreeModel myTreeModel;
  private Boolean myIsReadOnly = null;
  private boolean myExcluded = false;
  private String myText = null;

  protected Node(@NotNull DefaultTreeModel model) {
    myTreeModel = model;
  }

  /**
   * debug method for producing string tree presentation
   */
  public abstract String tree2string(int indent, String lineSeparator);

  protected abstract boolean isDataValid();
  protected abstract boolean isDataReadOnly();
  protected abstract boolean isDataExcluded();
  protected abstract String getText(UsageView view);

  public final boolean isValid() {
    return myIsValid;
  }

  public final boolean isReadOnly() {
    if (myIsReadOnly == null) myIsReadOnly = Boolean.valueOf(isDataReadOnly());
    return myIsReadOnly.booleanValue();
  }

  public final boolean isExcluded() {
    return myExcluded;
  }

  public final void update(UsageView view) {
    boolean isDataValid = isDataValid();
    boolean isReadOnly = isDataReadOnly();
    boolean isExcluded = isDataExcluded();
    String text = getText(view);
    if (isDataValid != myIsValid || myIsReadOnly == null || isReadOnly != myIsReadOnly.booleanValue() || isExcluded != myExcluded ||
      !Comparing.equal(myText, text)) {
      myIsValid = isDataValid;
      myExcluded = isExcluded;
      myIsReadOnly = Boolean.valueOf(isReadOnly);
      myText = text;
      updateNotify();
      myTreeModel.nodeChanged(this);
    }
  }

  /**
   * Override to perform node-specific updates 
   */
  protected void updateNotify() {
  }
}
