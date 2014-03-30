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

package com.intellij.ide.hierarchy;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public abstract class HierarchyTreeStructure extends AbstractTreeStructure {
  protected HierarchyNodeDescriptor myBaseDescriptor;
  private HierarchyNodeDescriptor myRoot;
  @NotNull
  protected final Project myProject;

  protected HierarchyTreeStructure(@NotNull Project project, HierarchyNodeDescriptor baseDescriptor) {
    myBaseDescriptor = baseDescriptor;
    myProject = project;
    myRoot = baseDescriptor;
  }

  public final HierarchyNodeDescriptor getBaseDescriptor() {
    return myBaseDescriptor;
  }

  protected final void setBaseElement(@NotNull HierarchyNodeDescriptor baseElement) {
    myBaseDescriptor = baseElement;
    myRoot = baseElement;
    while(myRoot.getParentDescriptor() != null){
      myRoot = (HierarchyNodeDescriptor)myRoot.getParentDescriptor();
    }
  }

  @Override
  @NotNull
  public final NodeDescriptor createDescriptor(final Object element, final NodeDescriptor parentDescriptor) {
    if (element instanceof HierarchyNodeDescriptor) {
      return (HierarchyNodeDescriptor)element;
    }
    if (element instanceof String) {
      return new TextInfoNodeDescriptor(parentDescriptor, (String)element, myProject);
    }
    throw new IllegalArgumentException("Unknown element type: " + element);
  }

  @Override
  public final boolean isToBuildChildrenInBackground(final Object element) {
    if (element instanceof HierarchyNodeDescriptor){
      final HierarchyNodeDescriptor descriptor = (HierarchyNodeDescriptor)element;
      final Object[] cachedChildren = descriptor.getCachedChildren();
      if (cachedChildren == null && descriptor.isValid()){
        return true;
      }
    }
    return false;
  }

  @Override
  public final Object[] getChildElements(final Object element) {
    if (element instanceof HierarchyNodeDescriptor) {
      final HierarchyNodeDescriptor descriptor = (HierarchyNodeDescriptor)element;
      final Object[] cachedChildren = descriptor.getCachedChildren();
      if (cachedChildren == null) {
        if (descriptor.isValid()) {
          descriptor.setCachedChildren(buildChildren(descriptor));
        }
        else {
          descriptor.setCachedChildren(ArrayUtil.EMPTY_OBJECT_ARRAY);
        }
      }
      return descriptor.getCachedChildren();
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public final Object getParentElement(final Object element) {
    if (element instanceof HierarchyNodeDescriptor) {
      return ((HierarchyNodeDescriptor)element).getParentDescriptor();
    }

    return null;
  }

  @Override
  public final void commit() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  @Override
  public final boolean hasSomethingToCommit() {
    return PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments();
  }
  @NotNull
  @Override
  public ActionCallback asyncCommit() {
    return asyncCommitDocuments(myProject);
  }

  @NotNull
  protected abstract Object[] buildChildren(@NotNull HierarchyNodeDescriptor descriptor);

  @Override
  public final Object getRootElement() {
    return myRoot;
  }

  protected SearchScope getSearchScope(final String scopeType, final PsiElement thisClass) {
    SearchScope searchScope = GlobalSearchScope.allScope(myProject);
    if (HierarchyBrowserBaseEx.SCOPE_CLASS.equals(scopeType)) {
      searchScope = new LocalSearchScope(thisClass);
    }
    else if (HierarchyBrowserBaseEx.SCOPE_PROJECT.equals(scopeType)) {
      searchScope = GlobalSearchScopesCore.projectProductionScope(myProject);
    }
    else if (HierarchyBrowserBaseEx.SCOPE_TEST.equals(scopeType)) {
      searchScope = GlobalSearchScopesCore.projectTestScope(myProject);
    } else {
      final NamedScope namedScope = NamedScopesHolder.getScope(myProject, scopeType);
      if (namedScope != null) {
        searchScope = GlobalSearchScopesCore.filterScope(myProject, namedScope);
      }
    }
    return searchScope;
  }

  protected boolean isInScope(final PsiElement baseClass, final PsiElement srcElement, final String scopeType) {
    if (HierarchyBrowserBaseEx.SCOPE_CLASS.equals(scopeType)) {
      if (!PsiTreeUtil.isAncestor(baseClass, srcElement, true)) {
        return false;
      }
    }
    else if (HierarchyBrowserBaseEx.SCOPE_PROJECT.equals(scopeType)) {
      final VirtualFile virtualFile = srcElement.getContainingFile().getVirtualFile();
      if (virtualFile != null && ProjectRootManager.getInstance(myProject).getFileIndex().isInTestSourceContent(virtualFile)) {
        return false;
      }
    }
    else if (HierarchyBrowserBaseEx.SCOPE_TEST.equals(scopeType)) {

      final VirtualFile virtualFile = srcElement.getContainingFile().getVirtualFile();
      if (virtualFile != null && !ProjectRootManager.getInstance(myProject).getFileIndex().isInTestSourceContent(virtualFile)) {
        return false;
      }
    } else if (!HierarchyBrowserBaseEx.SCOPE_ALL.equals(scopeType)) {
      final NamedScope namedScope = NamedScopesHolder.getScope(myProject, scopeType);
      if (namedScope == null) {
        return false;
      }
      final PackageSet namedScopePattern = namedScope.getValue();
      if (namedScopePattern == null) {
        return false;
      }
      final PsiFile psiFile = srcElement.getContainingFile();
      if (psiFile != null && !namedScopePattern.contains(psiFile, NamedScopesHolder.getHolder(myProject, scopeType, NamedScopeManager.getInstance(myProject)))) {
        return false;
      }
    }
    return true;
  }

  private static final class TextInfoNodeDescriptor extends NodeDescriptor {
    public TextInfoNodeDescriptor(final NodeDescriptor parentDescriptor, final String text, final Project project) {
      super(project, parentDescriptor);
      myName = text;
      myColor = JBColor.RED;
    }

    @Override
    public final Object getElement() {
      return myName;
    }

    @Override
    public final boolean update() {
      return true;
    }
  }

  public boolean isAlwaysShowPlus() {
    return false;
  }
}
