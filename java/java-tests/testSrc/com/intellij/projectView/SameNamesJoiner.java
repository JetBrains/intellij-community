/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import java.util.Iterator;

class SameNamesJoiner implements TreeStructureProvider {
  @NotNull
  @Override
  public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent, @NotNull Collection<AbstractTreeNode> children, ViewSettings settings) {
    if (parent instanceof JoinedNode) return children;

    ArrayList<AbstractTreeNode> result = new ArrayList<>();

    MultiValuesMap<Object, AbstractTreeNode> executed = new MultiValuesMap<>();
    for (Iterator<AbstractTreeNode> iterator = children.iterator(); iterator.hasNext();) {
      ProjectViewNode treeNode = (ProjectViewNode)iterator.next();
      Object o = treeNode.getValue();
      if (o instanceof PsiFile) {
        String name = ((PsiFile)o).getVirtualFile().getNameWithoutExtension();
        executed.put(name, treeNode);
      }
      else {
        executed.put(o, treeNode);
      }
    }

    Iterator<Object> keys = executed.keySet().iterator();
    while (keys.hasNext()) {
      Object each = keys.next();
      Collection<AbstractTreeNode> objects = executed.get(each);
      if (objects.size() > 1) {
        result.add(new JoinedNode(objects, new Joined(findPsiFileIn(objects))));
      }
      else if (objects.size() == 1) {
        result.add(objects.iterator().next());
      }
    }

    return result;
  }

  @Override
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }

  public PsiElement getTopLevelElement(final PsiElement element) {
    return null;
  }

  private PsiFile findPsiFileIn(Collection<AbstractTreeNode> objects) {
    for (Iterator<AbstractTreeNode> iterator = objects.iterator(); iterator.hasNext();) {
      AbstractTreeNode treeNode = iterator.next();
      if (treeNode.getValue() instanceof PsiFile) return (PsiFile)treeNode.getValue();
    }
    return null;
  }

  private boolean hasElementWithTheSameName(PsiFile element) {
    PsiDirectory psiDirectory = element.getParent();
    PsiElement[] children = psiDirectory.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (child != element && element.getVirtualFile().getNameWithoutExtension().equals(((PsiFile)child).getVirtualFile().getNameWithoutExtension())){
        return true;
      }
    }

    return false;
  }

  private class Joined{
    private final String myName;
    private final PsiFile myFile;

    public Joined(PsiFile file) {
      myFile = file;
      myName = file.getName();
    }

    public String toString() {
      return myFile.getVirtualFile().getNameWithoutExtension();
    }

    public PsiFile getPsiFile() {
      return myFile;
    }

    public boolean equals(Object object) {
      if (!(object instanceof Joined)) return false;
      return myFile.getVirtualFile().getNameWithoutExtension()
        .equals(((Joined)object).myFile.getVirtualFile().getNameWithoutExtension());
    }
  }

  private class JoinedNode extends ProjectViewNode<Joined>{
    Collection<AbstractTreeNode> myChildren;

    @Override
    @NotNull
    public Collection<AbstractTreeNode> getChildren() {
      return myChildren;
    }

    public JoinedNode(Collection<AbstractTreeNode> children, Joined formFile) {
      super(null, formFile, null);
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
    public void update(PresentationData presentation) {
    }
  }
}
