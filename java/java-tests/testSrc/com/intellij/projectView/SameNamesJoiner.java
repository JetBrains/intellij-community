// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

class SameNamesJoiner implements TreeStructureProvider {
  @NotNull
  @Override
  public Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent, @NotNull Collection<AbstractTreeNode<?>> children, ViewSettings settings) {
    if (parent instanceof JoinedNode) return children;

    ArrayList<AbstractTreeNode<?>> result = new ArrayList<>();

    MultiValuesMap<Object, AbstractTreeNode<?>> executed = new MultiValuesMap<>();
    for (AbstractTreeNode<?> child : children) {
      ProjectViewNode<?> treeNode = (ProjectViewNode)child;
      Object o = treeNode.getValue();
      if (o instanceof PsiFile) {
        String name = ((PsiFile)o).getVirtualFile().getNameWithoutExtension();
        executed.put(name, treeNode);
      }
      else {
        executed.put(o, treeNode);
      }
    }

    for (Object each : executed.keySet()) {
      Collection<AbstractTreeNode<?>> objects = executed.get(each);
      if (objects.size() > 1) {
        result.add(new JoinedNode(objects, new Joined(findPsiFileIn(objects)), settings));
      }
      else if (objects.size() == 1) {
        result.add(objects.iterator().next());
      }
    }

    return result;
  }

  public PsiElement getTopLevelElement(final PsiElement element) {
    return null;
  }

  private static PsiFile findPsiFileIn(Collection<AbstractTreeNode<?>> objects) {
    for (AbstractTreeNode treeNode : objects) {
      if (treeNode.getValue() instanceof PsiFile) return (PsiFile)treeNode.getValue();
    }
    return null;
  }

  private static boolean hasElementWithTheSameName(PsiFile element) {
    PsiDirectory psiDirectory = element.getParent();
    PsiElement[] children = psiDirectory.getChildren();
    for (PsiElement child : children) {
      if (child != element &&
          element.getVirtualFile().getNameWithoutExtension().equals(((PsiFile)child).getVirtualFile().getNameWithoutExtension())) {
        return true;
      }
    }

    return false;
  }

  private static class Joined{
    private final PsiFile myFile;

    Joined(PsiFile file) {
      myFile = file;
    }

    @Override
    public String toString() {
      return myFile.getVirtualFile().getNameWithoutExtension();
    }

    public PsiFile getPsiFile() {
      return myFile;
    }

    @Override
    public boolean equals(Object object) {
      if (!(object instanceof Joined)) return false;
      return myFile.getVirtualFile().getNameWithoutExtension()
        .equals(((Joined)object).myFile.getVirtualFile().getNameWithoutExtension());
    }
  }

  private static class JoinedNode extends ProjectViewNode<Joined>{
    Collection<AbstractTreeNode<?>> myChildren;

    @Override
    @NotNull
    public Collection<AbstractTreeNode<?>> getChildren() {
      return myChildren;
    }

    JoinedNode(Collection<AbstractTreeNode<?>> children, @NotNull Joined formFile, ViewSettings settings) {
      super(null, formFile, settings);
      myChildren = children;
    }

    @Override
    public String getTestPresentation() {
      return getValue().toString() + " joined";
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return false;
    }

    @Override
    public void update(@NotNull PresentationData presentation) {
    }
  }
}
