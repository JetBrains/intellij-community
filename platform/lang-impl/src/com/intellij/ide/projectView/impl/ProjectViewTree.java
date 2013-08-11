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

package com.intellij.ide.projectView.impl;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.JBTreeWithHintProvider;
import com.intellij.ui.tabs.FileColorManagerImpl;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ProjectViewTree extends JBTreeWithHintProvider {
  private final Project myProject;

  protected ProjectViewTree(Project project, TreeModel model) {
    super(model);
    myProject = project;

    final NodeRenderer cellRenderer = new NodeRenderer() {
      @Override
      protected void doPaint(Graphics2D g) {
        super.doPaint(g);
        setOpaque(false);
      }
    };
    cellRenderer.setOpaque(false);
    cellRenderer.setIconOpaque(false);
    setCellRenderer(cellRenderer);
    cellRenderer.setTransparentIconBackground(true);
  }

  public abstract DefaultMutableTreeNode getSelectedNode();

  public Project getProject() {
    return myProject;
  }

  @Override
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

  //@Override
  //public Color getBackground() {
  //  if (!UIUtil.isUnderDarcula()) {
  //    return super.getBackground();
  //  }
  //  return new ColorUIResource(0x414750);
  //}

  @Override
  public boolean isFileColorsEnabled() {
    return isFileColorsEnabledFor(this);
  }

  public static boolean isFileColorsEnabledFor(JTree tree) {
    final boolean enabled = FileColorManagerImpl._isEnabled() && FileColorManagerImpl._isEnabledForProjectView();
    final boolean opaque = tree.isOpaque();
    if (enabled && opaque) {
      tree.setOpaque(false);
    } else if (!enabled && !opaque) {
      tree.setOpaque(true);
    }
    return enabled;
  }

  @Nullable
  @Override
  public Color getFileColorFor(Object object) {
    return getColorForObject(object, getProject(), new NullableFunction<Object, PsiElement>() {
      @Override
      public PsiElement fun(Object object) {
        if (object instanceof AbstractTreeNode) {
          final Object element = ((AbstractTreeNode)object).getValue();
          if (element instanceof PsiElement) {
            return (PsiElement)element;
          }
        }
        return null;
      }
    });
  }

  @Nullable
  public static <T> Color getColorForObject(T object, Project project, @NotNull Function<T, PsiElement> converter) {
    Color color = null;
    final PsiElement psi = converter.fun(object);
    if (psi != null) {
      if (!psi.isValid()) return null;

      final VirtualFile file = PsiUtilCore.getVirtualFile(psi);

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
          } else if (c != null) {
            color = null;
            break;
          }
        }
      }
    }
    return color == null ? null : ColorUtil.softer(color);
  }
}
