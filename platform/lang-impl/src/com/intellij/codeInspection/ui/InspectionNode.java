/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.icons.AllIcons;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class InspectionNode extends InspectionTreeNode {
  public static final Icon TOOL = LayeredIcon.create(AllIcons.Toolwindows.ToolWindowInspection, IconUtil.getEmptyIcon(false));
  private boolean myTooBigForOnlineRefresh = false;

  public InspectionNode(@NotNull InspectionToolWrapper toolWrapper) {
    super(toolWrapper);
  }

  public String toString() {
    return getToolWrapper().getDisplayName();
  }

  @NotNull
  public InspectionToolWrapper getToolWrapper() {
    return (InspectionToolWrapper)getUserObject();
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return TOOL;
  }

  public boolean isTooBigForOnlineRefresh() {
    if (!myTooBigForOnlineRefresh) {
      myTooBigForOnlineRefresh = getProblemCount() > 1000;
    }
    return myTooBigForOnlineRefresh;
  }
}
