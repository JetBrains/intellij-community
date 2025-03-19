// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

public final class NestingTreeNode extends PsiFileNode implements FileNodeWithNestedFileNodes {
  private final @NotNull Collection<? extends PsiFileNode> myNestedFileNodes;

  public NestingTreeNode(@NotNull PsiFileNode originalNode, @NotNull Collection<? extends PsiFileNode> nestedFileNodes) {
    super(originalNode.getProject(), Objects.requireNonNull(originalNode.getValue()), originalNode.getSettings());
    myNestedFileNodes = nestedFileNodes;
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return true;
  }

  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }

  @Override
  public @NotNull Collection<AbstractTreeNode<?>> getNestedFileNodes() {
    ArrayList<AbstractTreeNode<?>> result = new ArrayList<>(myNestedFileNodes.size());
    for (PsiFileNode node : myNestedFileNodes) {
      PsiFile value = node.getValue();
      if (value != null) {
        result.add(new PsiFileNode(node.getProject(), value, node.getSettings()));
      }
    }
    return result;
  }

  @Override
  public Collection<AbstractTreeNode<?>> getChildrenImpl() {
    ArrayList<AbstractTreeNode<?>> result = new ArrayList<>(getNestedFileNodes());
    Collection<AbstractTreeNode<?>> superChildren = super.getChildrenImpl();
    if (superChildren != null) {
      result.addAll(superChildren);
    }
    return result;
  }

  @Override
  public boolean contains(final @NotNull VirtualFile file) {
    if (super.contains(file)) return true;

    for (PsiFileNode node : myNestedFileNodes) {
      final PsiFile psiFile = node.getValue();
      if (psiFile != null && file.equals(psiFile.getVirtualFile())) {
        return true;
      }
    }

    return false;
  }
}
