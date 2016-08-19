/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.ArrayList;
import java.util.Collection;

public class ClassesTreeStructureProvider implements SelectableTreeStructureProvider, DumbAware {
  private final Project myProject;

  public ClassesTreeStructureProvider(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent, @NotNull Collection<AbstractTreeNode> children, ViewSettings settings) {
    ArrayList<AbstractTreeNode> result = new ArrayList<>();
    for (final AbstractTreeNode child : children) {
      Object o = child.getValue();
      if (o instanceof PsiClassOwner && !(o instanceof ServerPageFile)) {
        final ViewSettings settings1 = ((ProjectViewNode)parent).getSettings();
        final PsiClassOwner classOwner = (PsiClassOwner)o;
        final VirtualFile file = classOwner.getVirtualFile();

        if (!(classOwner instanceof PsiCompiledElement)) {
          //do not show duplicated items if jar file contains classes and sources
          final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
          if (file != null && fileIndex.isInLibrarySource(file)) {
            final PsiElement originalElement = classOwner.getOriginalElement();
            if (originalElement instanceof PsiFile) {
              PsiFile classFile = (PsiFile)originalElement;
              final VirtualFile virtualClassFile = classFile.getVirtualFile();
              if (virtualClassFile != null && fileIndex.isInLibraryClasses(virtualClassFile)
                  && !classOwner.getManager().areElementsEquivalent(classOwner, classFile)
                  && classOwner.getManager().areElementsEquivalent(classOwner.getContainingDirectory(), classFile.getContainingDirectory())) {
                continue;
              }
            }
          }
        }

        if (fileInRoots(file)) {
          PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
            @Override
            public PsiClass[] compute() {
              try {
                return classOwner.getClasses();
              }
              catch (IndexNotReadyException e) {
                return PsiClass.EMPTY_ARRAY;
              }
            }
          });
          if (classes.length == 1 && !(classes[0] instanceof SyntheticElement) &&
              (file == null || file.getNameWithoutExtension().equals(classes[0].getName()))) {
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
    return file != null && (index.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) || index.isInLibraryClasses(file) || index.isInLibrarySource(file));
  }

  @Override
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }

  @Override
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

  private static boolean isSelectable(PsiElement element) {
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

  private static boolean isTopLevelClass(final PsiElement element, @NotNull PsiFile baseRootFile) {
    if (!(element instanceof PsiClass)) {
      return false;
    }
    return ClassUtil.isTopLevelClass((PsiClass)element);
  }

  private static class PsiClassOwnerTreeNode extends PsiFileNode {
    public PsiClassOwnerTreeNode(PsiClassOwner classOwner, ViewSettings settings) {
      super(classOwner.getProject(), classOwner, settings);
    }

    @Override
    public Collection<AbstractTreeNode> getChildrenImpl() {
      final ViewSettings settings = getSettings();
      final ArrayList<AbstractTreeNode> result = new ArrayList<>();
      for (PsiClass aClass : ((PsiClassOwner)getValue()).getClasses()) {
        if (!(aClass instanceof SyntheticElement)) {
          result.add(new ClassTreeNode(myProject, aClass, settings));
        }
      }
      return result;
    }

  }
}
