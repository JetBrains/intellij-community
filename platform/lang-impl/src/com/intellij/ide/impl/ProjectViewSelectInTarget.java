package com.intellij.ide.impl;

import com.intellij.ide.CompositeSelectInTarget;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.SelectableTreeStructureProvider;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class ProjectViewSelectInTarget extends SelectInTargetPsiWrapper implements CompositeSelectInTarget {
  private String mySubId;

  protected ProjectViewSelectInTarget(Project project) {
    super(project);
  }

  protected final void select(final Object selector, final VirtualFile virtualFile, final boolean requestFocus) {
    select(myProject, selector, getMinorViewId(), mySubId, virtualFile, requestFocus);
  }

  public static ActionCallback select(@NotNull Project project,
                            final Object toSelect,
                            @Nullable final String viewId,
                            @Nullable final String subviewId,
                            final VirtualFile virtualFile,
                            final boolean requestFocus) {
    final ActionCallback result = new ActionCallback();


    final ProjectView projectView = ProjectView.getInstance(project);
    ToolWindowManager windowManager=ToolWindowManager.getInstance(project);
    final ToolWindowEx projectViewToolWindow = (ToolWindowEx) windowManager.getToolWindow(ToolWindowId.PROJECT_VIEW);
    final Runnable runnable = new Runnable() {
      public void run() {
        if (requestFocus) {
          projectView.changeView(viewId, subviewId);
        }

        projectView.selectCB(toSelect, virtualFile, requestFocus).notify(result);
      }
    };

    if (requestFocus) {
      projectViewToolWindow.activate(runnable, false);
    } else {
      projectViewToolWindow.show(runnable);
    }

    return result;
  }


  @NotNull
  public Collection<SelectInTarget> getSubTargets(SelectInContext context) {
    List<SelectInTarget> result = new ArrayList<SelectInTarget>();
    AbstractProjectViewPane pane = ProjectView.getInstance(myProject).getProjectViewPaneById(getMinorViewId());
    int index = 0;
    for (String subId : pane.getSubIds()) {
      result.add(new ProjectSubViewSelectInTarget(this, subId, index++));
    }
    return result;
  }

  public boolean isSubIdSelectable(String subId, SelectInContext context) {
    return false;
  }

  @Override
  protected boolean canSelect(PsiFileSystemItem file) {
    final Project project = file.getProject();
    final String activeToolWindowId = ToolWindowManager.getInstance(project).getActiveToolWindowId();
    if (ToolWindowId.PROJECT_VIEW.equals(activeToolWindowId)) {
      final String currentView = ProjectView.getInstance(project).getCurrentViewId();
      if (Comparing.strEqual(currentView, getMinorViewId())) {
        return false;
      }
    }
    return true;
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
      if (element instanceof PsiFile || element instanceof PsiDirectory) {
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
