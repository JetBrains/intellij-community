package com.intellij.ide.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.SelectableTreeStructureProvider;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

public abstract class ProjectViewSelectInTarget extends SelectInTargetPsiWrapper {
  private String mySubId;

  protected ProjectViewSelectInTarget(Project project) {
    super(project);
  }

  protected final void select(final Object selector, final VirtualFile virtualFile, final boolean requestFocus) {
    final ProjectView projectView = ProjectView.getInstance(myProject);
    ToolWindowManager windowManager=ToolWindowManager.getInstance(myProject);
    final Runnable runnable = new Runnable() {
      public void run() {
        if (requestFocus) {
          projectView.changeView(getMinorViewId(), mySubId);
        }
        projectView.select(selector, virtualFile, requestFocus);
      }
    };
    if (requestFocus) {
      windowManager.getToolWindow(ToolWindowId.PROJECT_VIEW).activate(runnable, false);
    }
    else {
      runnable.run();
    }
  }

  @NotNull
  public String[] getSubIds() {
    AbstractProjectViewPane pane = ProjectView.getInstance(myProject).getProjectViewPaneById(getMinorViewId());
    return pane.getSubIds();
  }
  public boolean isSubIdSelectable(String subId, VirtualFile file) {
    return false;
  }
  public String getSubIdPresentableName(String subId) {
    AbstractProjectViewPane pane = ProjectView.getInstance(myProject).getProjectViewPaneById(getMinorViewId());
    return pane.getPresentableSubIdName(subId);
  }

  public void select(PsiElement element, final boolean requestFocus) {
    PsiElement toSelect = null;
    for (TreeStructureProvider provider : Extensions.getExtensions(TreeStructureProvider.EP_NAME, myProject)) {
      if (provider instanceof SelectableTreeStructureProvider) {
        toSelect = ((SelectableTreeStructureProvider) provider).getTopLevelElement(element);
      }
      if (toSelect != null) break;
    }
    if (toSelect == null) {
      if (element instanceof PsiFile) {
        toSelect = element;
      }
      else {
        final PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) return;
        final FileViewProvider viewProvider = containingFile.getViewProvider();
        toSelect = viewProvider.getPsi(viewProvider.getBaseLanguage());
      }
    }
    if (toSelect == null) return;
    PsiElement originalElement = toSelect.getOriginalElement();
    final VirtualFile virtualFile = PsiUtilBase.getVirtualFile(originalElement);
    select(originalElement, virtualFile, requestFocus);
  }

  public final String getToolWindowId() {
    return ToolWindowId.PROJECT_VIEW;
  }

  protected boolean canWorkWithCustomObjects() {
    return true;
  }
  public final void setSubId(String subId) {
    mySubId = subId;
  }
}
