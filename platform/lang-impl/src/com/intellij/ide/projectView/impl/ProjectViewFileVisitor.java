/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.tree.AbstractTreeNodeVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.util.function.Predicate;

import static com.intellij.psi.util.PsiUtilCore.getVirtualFile;

class ProjectViewFileVisitor extends AbstractTreeNodeVisitor<VirtualFile> {
  public ProjectViewFileVisitor(@NotNull VirtualFile file, Predicate<TreePath> predicate) {
    super(() -> file, predicate);
  }

  @Override
  protected boolean contains(@NotNull AbstractTreeNode node, @NotNull VirtualFile file) {
    return node instanceof ProjectViewNode && contains((ProjectViewNode)node, file) || super.contains(node, file);
  }

  private static boolean contains(@NotNull ProjectViewNode node, @NotNull VirtualFile file) {
    return node.contains(file);
  }

  @Override
  protected VirtualFile getContent(@NotNull AbstractTreeNode node) {
    Object value = node.getValue();
    return value instanceof PsiElement ? getVirtualFile((PsiElement)value) : null;
  }

  @Override
  protected boolean isAncestor(@NotNull VirtualFile content, @NotNull VirtualFile file) {
    return VfsUtilCore.isAncestor(content, file, true);
  }
}
