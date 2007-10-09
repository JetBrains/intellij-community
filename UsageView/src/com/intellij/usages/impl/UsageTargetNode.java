/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultTreeModel;

/**
 * @author max
 */
public class UsageTargetNode extends Node {
  private UsageTarget myTarget;

  public UsageTargetNode(@NotNull UsageTarget target, @NotNull DefaultTreeModel model) {
    super(model);
    myTarget = target;
    setUserObject(target);
  }

  public String tree2string(int indent, String lineSeparator) {
    return myTarget.getName();
  }

  protected boolean isDataValid() {
    return myTarget.isValid();
  }

  protected boolean isDataReadOnly() {
    return myTarget.isReadOnly();
  }

  protected boolean isDataExcluded() {
    return false;
  }

  protected String getText(final UsageView view) {
    return myTarget.getPresentation().getPresentableText();
  }

  public UsageTarget getTarget() {
    return myTarget;
  }

  protected void updateNotify() {
    super.updateNotify();
    myTarget.update();
  }
}
