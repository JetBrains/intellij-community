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

package com.intellij.codeInspection.ui;

import com.intellij.util.IconUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
class InspectionGroupNode extends InspectionTreeNode {
  private static final Icon EMPTY = new EmptyIcon(0, IconUtil.getEmptyIcon(false).getIconHeight());

  InspectionGroupNode(@NotNull String groupTitle) {
    super(groupTitle);
  }

  String getGroupTitle() {
    return (String) getUserObject();
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return EMPTY;
  }

  @Override
  public boolean appearsBold() {
    return true;
  }
}
