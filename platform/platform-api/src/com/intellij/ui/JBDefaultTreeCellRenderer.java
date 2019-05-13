/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Color;

/**
 * Extend this class instead of DefaultTreeCellRenderer
 */
public class JBDefaultTreeCellRenderer extends DefaultTreeCellRenderer {
  private final boolean myWideSelection;

  public JBDefaultTreeCellRenderer() {
    this(true);
  }

  public JBDefaultTreeCellRenderer(@NotNull final JTree tree) {
    this(WideSelectionTreeUI.isWideSelection(tree));
  }

  public JBDefaultTreeCellRenderer(boolean isWideSelection) {
    myWideSelection = isWideSelection;
    if (isWideSelection) {
      setOpaque(false);
      ReflectionUtil.setField(DefaultTreeCellRenderer.class, this, boolean.class, "fillBackground", false);
    }
  }

  @Override
  public Color getBorderSelectionColor() {
    return myWideSelection ? null : super.getBorderSelectionColor();
  }

  protected Color getSelectionForeground(@NotNull final JTree tree) {
    return myWideSelection && !tree.hasFocus() ? UIUtil.getTreeForeground() : UIUtil.getTreeSelectionForeground();
  }
}
