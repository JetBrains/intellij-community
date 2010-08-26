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
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.SelectableTreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public class ClassesTreeStructureProvider implements SelectableTreeStructureProvider, DumbAware {
  private final Project myProject;

  public ClassesTreeStructureProvider(Project project) {
    myProject = project;
  }

  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (final AbstractTreeNode child : children) {
      Object o = child.getValue();
      if (o instanceof PsiClassOwner) {
        final ViewSettings settings1 = ((ProjectViewNode)parent).getSettings();
        final PsiClassOwner classOwner = (PsiClassOwner)o;
        PsiClass[] classes = classOwner.getClasses();
        final VirtualFile file = classOwner.getVirtualFile();
        if (fileInRoots(file)) {
          if (classes.length == 1 && !(classes[0] instanceof SyntheticElement)) {
            result.add(new ClassTreeNode(myProject, classes[0], settings1));
          } else {
            result.add(new PsiClassOwnerTreeNode(classOwner, settings1));
          }
          continue;
        }
      }
      result.add(child);
    }
    return result;
  }

  private boolean fileInRoots(VirtualFile file) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    return file != null &&
        (index.isInSourceContent(file) || index.isInLibraryClasses(file) || index.isInLibrarySource(file));
  }

  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }

  public PsiElement getTopLevelElement(final PsiElement element) {
    PsiFile baseRootFile = getBaseRootFile(element);
    if (baseRootFile == null) return null;

    if (!fileInRoots(baseRootFile.getVirtualFile())) return baseRootFile;

    PsiElement current = element;
    while (current != null) {

      if (isSelectable(current)) break;
      if (isTopLevelClass(current, baseRootFile)) break;

      current = current.getParent();
    }

    if (current instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner)current).getClasses();
      if (classes.length == 1 && !(classes[0] instanceof SyntheticElement) && isTopLevelClass(classes[0], baseRootFile)) {
        current = classes[0];
      }
    }

    return current != null ? current : baseRootFile;
  }

  private boolean isSelectable(PsiElement element) {
    if (element instanceof PsiFileSystemItem) return true;

    if (element instanceof PsiField || element instanceof PsiClass || element instanceof PsiMethod) {
      return !(element.getParent() instanceof PsiAnonymousClass) && !(element instanceof PsiAnonymousClass);
    }

    return false;
  }

  @Nullable
  private static PsiFile getBaseRootFile(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;

    final FileViewProvider viewProvider = containingFile.getViewProvider();
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  private static boolean isTopLevelClass(final PsiElement element, PsiFile baseRootFile) {

    if (!(element instanceof PsiClass)) {
      return false;
    }

    if (element instanceof PsiAnonymousClass) {
      return false;
    }

    final PsiFile parentFile = parentFileOf((PsiClass)element);
                                        // do not select JspClass
    return parentFile != null && parentFile.getLanguage() == baseRootFile.getLanguage();
  }

  @Nullable
  private static PsiFile parentFileOf(final PsiClass psiClass) {
    return psiClass.getContainingClass() == null ? psiClass.getContainingFile() : null;
  }

  private static class PsiClassOwnerTreeNode extends PsiFileNode {

    public PsiClassOwnerTreeNode(PsiClassOwner classOwner, ViewSettings settings) {
      super(classOwner.getProject(), classOwner, settings);
    }

    @Override
    public Collection<AbstractTreeNode> getChildrenImpl() {
      final ViewSettings settings = getSettings();
      final ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
      for (PsiClass aClass : ((PsiClassOwner)getValue()).getClasses()) {
        if (!(aClass instanceof SyntheticElement)) {
          result.add(new ClassTreeNode(myProject, aClass, settings));
        }
      }
      return result;
    }
    
  }
}
