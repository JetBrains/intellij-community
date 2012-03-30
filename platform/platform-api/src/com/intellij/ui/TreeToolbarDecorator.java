/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
class TreeToolbarDecorator extends ToolbarDecorator {
  private final JTree myTree;

  TreeToolbarDecorator(JTree tree) {
    myTree = tree;
  }

  @Override
  public ToolbarDecorator initPositionAndBorder() {
    return super.initPositionAndBorder()
      .setToolbarPosition(SystemInfo.isMac ? ActionToolbarPosition.BOTTOM : ActionToolbarPosition.TOP);
  }

  @Override
  protected JComponent getComponent() {
    return myTree;
  }

  @Override
  protected void updateButtons() {
  }
}
