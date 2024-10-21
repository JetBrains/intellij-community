// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.scopeView;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.tree.project.ProjectFileNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ScopePaneSelectInTarget extends ProjectViewSelectInTarget {
  public ScopePaneSelectInTarget(final Project project) {
    super(project);
  }

  public String toString() {
    return IdeBundle.message("select.in.scope");
  }

  @Override
  public boolean canSelect(PsiFileSystemItem fileSystemItem) {
    VirtualFile file = PsiUtilCore.getVirtualFile(fileSystemItem);
    if (file == null || !file.isValid()) return false;
    file = BackedVirtualFile.getOriginFileIfBacked(file);
    AreaInstance area = ProjectFileNode.findArea(file, myProject);
    if (area == null) return false;
    return getContainingFilter(file) != null;
  }

  private @Nullable NamedScopeFilter getContainingFilter(@Nullable VirtualFile file) {
    if (file == null) return null;
    ScopeViewPane pane = getScopeViewPane();
    if (pane == null) return null;
    for (NamedScopeFilter filter : pane.getFilters()) {
      if (filter.accept(file)) return filter;
    }
    return null;
  }

  @Override
  public void select(PsiElement element, boolean requestFocus) {
    if (getSubId() == null) {
      LOG.debug("getSubId() == null, looking for a fallback");
      PsiFile file = element.getContainingFile();
      NamedScopeFilter filter = getContainingFilter(file == null ? null : file.getVirtualFile());
      if (LOG.isDebugEnabled()) {
        LOG.debug("The fallback is " + filter);
      }
      if (filter == null) return;
      setSubId(filter.toString());
    }
    super.select(element, requestFocus);
  }

  @Override
  public String getMinorViewId() {
    return ScopeViewPane.ID;
  }

  @Override
  public float getWeight() {
    return StandardTargetWeights.SCOPE_WEIGHT;
  }

  @Override
  public boolean isSubIdSelectable(@NotNull String subId, @NotNull SelectInContext context) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("ScopePaneSelectInTarget.isSubIdSelectable: subId=" + subId + ", context is " + context);
    }
    PsiFileSystemItem file = getContextPsiFile(context);
    if (!(file instanceof PsiFile)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Can NOT select " + file + " because it's not a PsiFile");
      }
      return false;
    }
    ScopeViewPane pane = getScopeViewPane();
    NamedScopeFilter filter = pane == null ? null : pane.getFilter(subId);
    if (filter == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Can NOT select " + file + " because filter is null");
      }
      return false;
    }
    boolean accept = filter.accept(file.getVirtualFile());
    if (LOG.isDebugEnabled()) {
      LOG.debug("The filter " + filter + (accept ? "accepts" : "does NOT accept") + " file " + file);
    }
    return accept;
  }

  private ScopeViewPane getScopeViewPane() {
    ProjectView view = ProjectView.getInstance(myProject);
    Object pane = view == null ? null : view.getProjectViewPaneById(ScopeViewPane.ID);
    return pane instanceof ScopeViewPane ? (ScopeViewPane)pane : null;
  }
}
