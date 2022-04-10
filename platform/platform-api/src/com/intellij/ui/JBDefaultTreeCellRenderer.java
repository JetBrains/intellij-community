// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
 * @deprecated use {@link com.intellij.ui.render.LabelBasedRenderer.Tree} instead
 */
@Deprecated(forRemoval = true)
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
