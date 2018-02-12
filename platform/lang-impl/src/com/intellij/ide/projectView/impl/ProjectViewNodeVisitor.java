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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.tree.AbstractTreeNodeVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.util.function.Predicate;

import static com.intellij.psi.SmartPointerManager.createPointer;
import static com.intellij.psi.util.PsiUtilCore.getVirtualFile;

class ProjectViewNodeVisitor extends AbstractTreeNodeVisitor<PsiElement> {
  private final VirtualFile file;

  public ProjectViewNodeVisitor(@NotNull PsiElement element, VirtualFile file, Predicate<TreePath> predicate) {
    super(createPointer(element)::getElement, predicate);
    this.file = file;
  }

  @Override
  protected boolean contains(@NotNull AbstractTreeNode node, @NotNull PsiElement element) {
    return node instanceof ProjectViewNode && contains((ProjectViewNode)node, element) || super.contains(node, element);
  }

  private boolean contains(@NotNull ProjectViewNode node, @NotNull PsiElement element) {
    return contains(node, this.file) || contains(node, getVirtualFile(element));
  }

  private static boolean contains(@NotNull ProjectViewNode node, VirtualFile file) {
    return file != null && node.contains(file);
  }

  @Override
  protected PsiElement getContent(@NotNull AbstractTreeNode node) {
    Object value = node.getValue();
    return value instanceof PsiElement ? (PsiElement)value : null;
  }

  @Override
  protected boolean isAncestor(@NotNull PsiElement content, @NotNull PsiElement element) {
    return PsiTreeUtil.isAncestor(content, element, true);
  }
}
