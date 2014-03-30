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

package com.intellij.ui;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * User: lex
 * Date: Sep 20, 2003
 * Time: 5:14:07 PM
 */
public class CheckedTreeNode extends DefaultMutableTreeNode {
  protected boolean isChecked = true;
  private boolean isEnabled = true;

  public CheckedTreeNode() {
  }

  public CheckedTreeNode(Object userObject) {
    super(userObject);
  }

  public boolean isChecked() {
    return isChecked;
  }


  public void setChecked(boolean checked) {
    isChecked = checked;
  }

  public void setEnabled(final boolean enabled) {
    isEnabled = enabled;
  }

  public boolean isEnabled() {
    return isEnabled;
  }
}
