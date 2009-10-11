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
package com.intellij.slicer;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.util.Icons;

/**
 * @author cdr
 */
public class AnalyzeLeavesAction extends ToggleAction {
  private final SliceTreeBuilder myTreeBuilder;

  public AnalyzeLeavesAction(SliceTreeBuilder treeBuilder) {
    super("Group by leaf expression", "Show original expression values that might appear in this place", Icons.XML_TAG_ICON);
    myTreeBuilder = treeBuilder;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myTreeBuilder.splitByLeafExpressions;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    if (state) {
      myTreeBuilder.switchToSplittedNodes();
    }
    else {
      myTreeBuilder.switchToUnsplittedNodes();
    }
  }
}
