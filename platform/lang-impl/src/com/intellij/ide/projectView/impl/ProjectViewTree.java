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

package com.intellij.ide.projectView.impl;

import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.tabs.FileColorManagerImpl;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ProjectViewTree extends DnDAwareTree {
  protected ProjectViewTree(TreeModel model) {
    super(model);

    final NodeRenderer cellRenderer = new NodeRenderer();
    cellRenderer.setOpaque(false);
    cellRenderer.setIconOpaque(false);
    setCellRenderer(cellRenderer);
    cellRenderer.setTransparentIconBackground(true);
    setOpaque(false);
  }

  public abstract DefaultMutableTreeNode getSelectedNode();

  public final int getToggleClickCount() {
    final DefaultMutableTreeNode selectedNode = getSelectedNode();
    if (selectedNode != null) {
      final Object object = selectedNode.getUserObject();
      if (object instanceof NodeDescriptor) {
        NodeDescriptor descriptor = (NodeDescriptor)object;
        if (!descriptor.expandOnDoubleClick()) {
          return -1;
        }
      }
    }
    return super.getToggleClickCount();
  }

  @Override
  public boolean isFileColorsEnabled() {
    return FileColorManagerImpl._isEnabled() && FileColorManagerImpl._isEnabledForProjectView();
  }

  @Nullable
  @Override
  protected Color getFileColorFor(Object object) {
    Color color = null;
    if (object instanceof AbstractTreeNode) {
      final Object element = ((AbstractTreeNode)object).getValue();
      if (element instanceof PsiElement) {
        final PsiElement psi = (PsiElement)element;
        final Project project = psi.getProject();
        final PsiFile file = psi.getContainingFile();

        if (file != null) {
          color = FileColorManager.getInstance(project).getFileColor(file);
        } else if (psi instanceof PsiDirectory) {
          color = FileColorManager.getInstance(project).getFileColor(((PsiDirectory)psi).getVirtualFile());
        } else if (psi instanceof PsiDirectoryContainer) {
          final PsiDirectory[] dirs = ((PsiDirectoryContainer)psi).getDirectories();
          for (PsiDirectory dir : dirs) {
            Color c = FileColorManager.getInstance(project).getFileColor(dir.getVirtualFile());
            if (c != null && color == null) {
              color = c;
            } else if (c != null && color != null) {
              color = null;
              break;
            }
          }
        }
      }
    }
    return color;
  }
}
