/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * Extend this class instead of DefaultTreeCellRenderer
 */
public class JBDefaultTreeCellRenderer extends DefaultTreeCellRenderer {

  private boolean myMacTreeUI;

  public JBDefaultTreeCellRenderer(@NotNull final JTree tree) {
    MacUIUtil.doNotFillBackground(tree, this);
    myMacTreeUI = tree.getUI() instanceof UIUtil.MacTreeUI && ((UIUtil.MacTreeUI)tree.getUI()).isWideSelection();
  }

  @Override
  public Color getBorderSelectionColor() {
    return myMacTreeUI ? null : super.getBorderSelectionColor();
  }

  protected Color getSelectionForeground(@NotNull final JTree tree) {
    return myMacTreeUI && !tree.hasFocus() ? UIUtil.getTreeForeground() : UIUtil.getTreeSelectionForeground();
  }
}
