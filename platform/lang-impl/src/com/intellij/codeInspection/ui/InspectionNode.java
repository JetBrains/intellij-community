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

package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;

import javax.swing.*;
import java.util.Enumeration;

/**
 * @author max
 */
public class InspectionNode extends InspectionTreeNode {
  public static final Icon TOOL;

  static {
    TOOL = LayeredIcon.create(IconLoader.getIcon("/general/toolWindowInspection.png"), IconUtil.getEmptyIcon(false));
  }

  public InspectionNode(InspectionTool tool) {
    super(tool);
  }

  public String toString() {
    return getTool().getDisplayName();
  }

  public InspectionTool getTool() {
    return (InspectionTool)getUserObject();
  }

  public Icon getIcon(boolean expanded) {
    return TOOL;
  }

  public int getProblemCount() {
    int sum = 0;
    Enumeration children = children();
    while (children.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)children.nextElement();
      if (child instanceof InspectionNode) continue;
      sum += child.getProblemCount();
    }
    return sum;
  }
}
