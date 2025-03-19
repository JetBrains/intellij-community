// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.hierarchy;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.tree.LeafState;
import com.intellij.usageView.UsageViewLongNameLocation;
import com.intellij.usageView.UsageViewTypeLocation;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

public abstract class HierarchyTreeStructure extends AbstractTreeStructure {
  protected HierarchyNodeDescriptor myBaseDescriptor;
  private HierarchyNodeDescriptor myRoot;
  protected final @NotNull Project myProject;

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
  public final @NotNull NodeDescriptor createDescriptor(@NotNull Object element, NodeDescriptor parentDescriptor) {
    if (element instanceof HierarchyNodeDescriptor) {
      return (HierarchyNodeDescriptor)element;
    }
    if (element instanceof String) {
      return new TextInfoNodeDescriptor(parentDescriptor, (String)element, myProject);
    }
    throw new IllegalArgumentException("Unknown element type: " + element);
  }

  @Override
  public final boolean isToBuildChildrenInBackground(@NotNull Object element) {
    if (element instanceof HierarchyNodeDescriptor descriptor){
      Object[] cachedChildren = descriptor.getCachedChildren();
      return cachedChildren == null && descriptor.isValid();
    }
    return false;
  }

  @Override
  public final Object @NotNull [] getChildElements(@NotNull Object element) {
    if (element instanceof HierarchyNodeDescriptor descriptor) {
      Object[] cachedChildren = descriptor.getCachedChildren();
      if (cachedChildren == null) {
        if (descriptor.isValid()) {
          try {
            cachedChildren = buildChildren(descriptor);
          }
          catch (IndexNotReadyException e) {
            return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
          }
        }
        else {
          cachedChildren = ArrayUtilRt.EMPTY_OBJECT_ARRAY;
        }
        descriptor.setCachedChildren(cachedChildren);
      }
      return cachedChildren;
    }
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public final Object getParentElement(@NotNull Object element) {
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
  @Override
  public @NotNull ActionCallback asyncCommit() {
    return asyncCommitDocuments(myProject);
  }

  protected abstract Object @NotNull [] buildChildren(@NotNull HierarchyNodeDescriptor descriptor);

  @Override
  public final @NotNull Object getRootElement() {
    return myRoot;
  }

  protected SearchScope getSearchScope(String scopeType, PsiElement thisClass) {
    SearchScope searchScope = GlobalSearchScope.allScope(myProject);
    if (HierarchyBrowserBaseEx.SCOPE_CLASS.equals(scopeType)) {
      searchScope = new LocalSearchScope(thisClass);
    }
    else if (HierarchyBrowserBaseEx.SCOPE_MODULE.equals(scopeType)) {
      Module module = ModuleUtilCore.findModuleForPsiElement(thisClass);
      searchScope = module == null ? new LocalSearchScope(thisClass) : module.getModuleScope(true);
    }
    else if (HierarchyBrowserBaseEx.SCOPE_PROJECT.equals(scopeType)) {
      searchScope = GlobalSearchScopesCore.projectProductionScope(myProject);
    }
    else if (HierarchyBrowserBaseEx.SCOPE_TEST.equals(scopeType)) {
      searchScope = GlobalSearchScopesCore.projectTestScope(myProject);
    } else {
      NamedScope namedScope = NamedScopesHolder.getScope(myProject, scopeType);
      if (namedScope != null) {
        searchScope = GlobalSearchScopesCore.filterScope(myProject, namedScope);
      }
    }
    return searchScope;
  }

  protected boolean isInScope(PsiElement baseClass, @NotNull PsiElement srcElement, String scopeType) {
    if (HierarchyBrowserBaseEx.SCOPE_CLASS.equals(scopeType)) {
      return PsiTreeUtil.isAncestor(baseClass, srcElement, true);
    }
    if (HierarchyBrowserBaseEx.SCOPE_MODULE.equals(scopeType)) {
      Module module = ModuleUtilCore.findModuleForPsiElement(baseClass);
      VirtualFile virtualFile = srcElement.getContainingFile().getVirtualFile();
      return module != null && module.getModuleScope().contains(virtualFile);
    }
    if (HierarchyBrowserBaseEx.SCOPE_PROJECT.equals(scopeType)) {
      VirtualFile virtualFile = srcElement.getContainingFile().getVirtualFile();
      return virtualFile == null || !TestSourcesFilter.isTestSources(virtualFile, myProject);
    }
    if (HierarchyBrowserBaseEx.SCOPE_TEST.equals(scopeType)) {
      VirtualFile virtualFile = srcElement.getContainingFile().getVirtualFile();
      return virtualFile == null || TestSourcesFilter.isTestSources(virtualFile, myProject);
    }
    if (HierarchyBrowserBaseEx.SCOPE_ALL.equals(scopeType)) {
      return true;
    }
    NamedScope namedScope = NamedScopesHolder.getScope(myProject, scopeType);
    if (namedScope == null) {
      return false;
    }
    PackageSet namedScopePattern = namedScope.getValue();
    if (namedScopePattern == null) {
      return false;
    }
    PsiFile psiFile = srcElement.getContainingFile();
    if (psiFile == null) {
      return true;
    }
    NamedScopesHolder holder = NamedScopesHolder.getHolder(myProject, scopeType, NamedScopeManager.getInstance(myProject));
    return namedScopePattern.contains(psiFile, holder);
  }

  private static final class TextInfoNodeDescriptor extends NodeDescriptor {
    TextInfoNodeDescriptor(NodeDescriptor parentDescriptor, String text, Project project) {
      super(project, parentDescriptor);
      myName = text;
      myColor = JBColor.RED;
    }

    @Override
    public Object getElement() {
      return myName;
    }

    @Override
    public boolean update() {
      return true;
    }
  }

  @Override
  public @NotNull LeafState getLeafState(@NotNull Object element) {
    if (isAlwaysShowPlus()) return LeafState.NEVER;
    LeafState state = super.getLeafState(element);
    return state != LeafState.DEFAULT ? state : LeafState.ASYNC;
  }

  public boolean isAlwaysShowPlus() {
    return false;
  }

  protected @NotNull String formatBaseElementText() {
    HierarchyNodeDescriptor descriptor = getBaseDescriptor();
    if (descriptor == null) return toString();
    PsiElement element = descriptor.getPsiElement();
    if (element == null) return descriptor.toString();
    return ElementDescriptionUtil.getElementDescription(element, UsageViewTypeLocation.INSTANCE) + " " +
           ElementDescriptionUtil.getElementDescription(element, UsageViewLongNameLocation.INSTANCE);
  }
}
