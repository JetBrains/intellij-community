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
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.tree.AbstractTreeNodeVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

class ProjectViewNodeVisitor extends AbstractTreeNodeVisitor<PsiElement> {
  public ProjectViewNodeVisitor(@NotNull PsiElement element) {
    super(create(element), (Predicate<TreePath>)null);
  }

  public ProjectViewNodeVisitor(@NotNull PsiElement element, Predicate<TreePath> predicate) {
    super(create(element), predicate);
  }

  public ProjectViewNodeVisitor(@NotNull PsiElement element, Consumer<TreePath> consumer) {
    super(create(element), consumer);
  }

  @Override
  protected boolean contains(@NotNull AbstractTreeNode node, @NotNull PsiElement element) {
    return node instanceof ProjectViewNode && contains((ProjectViewNode)node, element) || super.contains(node, element);
  }

  protected boolean contains(@NotNull ProjectViewNode node, @NotNull PsiElement element) {
    PsiFile pf = element.getContainingFile();
    if (pf != null) {
      VirtualFile vf = pf.getVirtualFile();
      if (vf != null) return node.contains(vf);
    }
    LOG.debug("no virtual file for element ", element);
    return false;
  }

  @Override
  protected boolean isAncestor(Object value, @NotNull PsiElement element) {
    return super.isAncestor(value, element);
  }

  private static Supplier<PsiElement> create(@NotNull PsiElement element) {
    SmartPointerManager manager = SmartPointerManager.getInstance(element.getProject());
    SmartPsiElementPointer<PsiElement> pointer = manager.createSmartPsiElementPointer(element);
    return pointer::getElement;
  }
}
