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

package com.intellij.ide.hierarchy;

import com.intellij.psi.PsiFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.FileColorManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public final class HierarchyNodeRenderer extends ColoredTreeCellRenderer {
  public void customizeCellRenderer(final JTree tree, final Object value, final boolean selected, final boolean expanded, final boolean leaf,
                                    final int row, final boolean hasFocus) {
    if (value instanceof DefaultMutableTreeNode) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      final Object object = node.getUserObject();
      if (object instanceof HierarchyNodeDescriptor) {
        final HierarchyNodeDescriptor descriptor = (HierarchyNodeDescriptor)object;
        descriptor.getHighlightedText().customize(this);
        if (expanded){
          setIcon(descriptor.getOpenIcon());
        }
        else{
          setIcon(descriptor.getClosedIcon());
        }
        if (!selected)  {
          final Color color = getBackgroundColor(descriptor);
          if (color != null) {
            setBackground(color);
          }
        }
      }
    }
  }

  @Nullable
  private static Color getBackgroundColor(final HierarchyNodeDescriptor descriptor) {
    final PsiFile psiFile = descriptor.getContainingFile();
    if (psiFile != null && psiFile.isValid()) {
      final FileColorManager colorManager = FileColorManager.getInstance(descriptor.getProject());
      if (colorManager.isEnabled()) {
        return colorManager.getFileColor(psiFile);
      }
    }
    return null;
  }
}
