// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.ide.CompositeSelectInTarget;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.SelectableTreeStructureProvider;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.SelectInProjectViewImpl;
import com.intellij.ide.projectView.impl.SelectInProjectViewImplKt;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.ide.projectView.impl.ProjectViewPane.canBeSelectedInProjectView;
import static com.intellij.psi.SmartPointerManager.createPointer;

public abstract class ProjectViewSelectInTarget extends SelectInTargetPsiWrapper implements CompositeSelectInTarget {
  private String mySubId;

  protected ProjectViewSelectInTarget(Project project) {
    super(project);
  }

  @Override
  protected final void select(final Object selector, final VirtualFile virtualFile, final boolean requestFocus) {
    select(myProject, selector, getMinorViewId(), mySubId, virtualFile, requestFocus);
  }

  public static @NotNull ActionCallback select(@NotNull Project project,
                                               final Object toSelect,
                                               final @Nullable String viewId,
                                               final @Nullable String subviewId,
                                               final VirtualFile virtualFile,
                                               final boolean requestFocus) {
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug(
        "ProjectViewSelectInTarget.select: " +
        "project=" + project +
        ", toSelect=" + toSelect +
        ", viewId=" + viewId +
        ", subviewId=" + subviewId +
        ", virtualFile=" + virtualFile +
        ", requestFocus=" + requestFocus
      );
    }
    ProjectView projectView = ProjectView.getInstance(project);
    if (projectView == null) {
      SelectInProjectViewImplKt.getLOG().debug("Not selecting anything because there is no project view");
      return ActionCallback.REJECTED;
    }

    String id = ObjectUtils.chooseNotNull(viewId, projectView.getDefaultViewId());
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      AbstractProjectViewPane pane = projectView.getProjectViewPaneById(id);
      if (pane != null) {
        if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
          SelectInProjectViewImplKt.getLOG().debug("Switching to pane " + pane);
        }
        pane.select(toSelect, virtualFile, requestFocus);
      }
      return ActionCallback.DONE;
    }

    Supplier<Object> toSelectSupplier = toSelect instanceof PsiElement
                                        ? createPointer((PsiElement)toSelect)::getElement
                                        : () -> toSelect;

    ToolWindow projectViewToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
    if (projectViewToolWindow == null) {
      SelectInProjectViewImplKt.getLOG().debug("Not selecting anything because there is no project view tool window");
      return ActionCallback.REJECTED;
    }

    ActionCallback result = new ActionCallback();
    Runnable runnable = () -> {
      if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
        SelectInProjectViewImplKt.getLOG().debug(
          (requestFocus ? "Activated" : "Shown") +
          ". Changing project view to " + id + " / " + subviewId + ", will continue once changed"
        );
      }
      projectView.changeViewCB(id, subviewId).doWhenProcessed(() -> {
        SelectInProjectViewImplKt.getLOG().debug("Changed. Delegating to SelectInProjectViewImpl to continue");
        project.getService(SelectInProjectViewImpl.class).ensureSelected(id, virtualFile, toSelectSupplier, requestFocus, true, result);
      });
    };

    if (requestFocus) {
      SelectInProjectViewImplKt.getLOG().debug("Activating the project view tool window, will continue once activated");
      projectViewToolWindow.activate(runnable, true);
    }
    else {
      SelectInProjectViewImplKt.getLOG().debug("Showing the project view tool window, will continue once shown");
      projectViewToolWindow.show(runnable);
    }

    return result;
  }

  @Override
  public @NotNull Collection<SelectInTarget> getSubTargets(@NotNull SelectInContext context) {
    List<SelectInTarget> result = new ArrayList<>();
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
    VirtualFile vFile = PsiUtilCore.getVirtualFile(file);
    vFile = vFile == null ? null : BackedVirtualFile.getOriginFileIfBacked(vFile);
    if (vFile == null) {
      if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
        SelectInProjectViewImplKt.getLOG().debug("Can NOT select " + file + " because its virtual file is null");
      }
      return false;
    }
    else if (!vFile.isValid()) {
      if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
        SelectInProjectViewImplKt.getLOG().debug("Can NOT select " + file + " because its virtual file " + vFile + " is invalid");
      }
      return false;
    }

    return canBeSelectedInProjectView(myProject, vFile);
  }

  public @Nls String getSubIdPresentableName(String subId) {
    AbstractProjectViewPane pane = ProjectView.getInstance(myProject).getProjectViewPaneById(getMinorViewId());
    return pane.getPresentableSubIdName(subId);
  }

  @Override
  public void select(PsiElement element, final boolean requestFocus) {
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug(
        "ProjectViewSelectInTarget.selectIn: Select in " + this +
        ", requestFocus=" + requestFocus +
        ", element=" + element
      );
    }
    PsiUtilCore.ensureValid(element);
    PsiElement toSelect = null;
    for (TreeStructureProvider provider : getProvidersDumbAware()) {
      if (provider instanceof SelectableTreeStructureProvider) {
        toSelect = ((SelectableTreeStructureProvider)provider).getTopLevelElement(element);
      }
      if (toSelect != null) {
        if (!toSelect.isValid()) {
          throw new PsiInvalidElementAccessException(toSelect, "Returned by " + provider);
        }
        break;
      }
    }

    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug("Top level element is " + toSelect);
    }
    toSelect = findElementToSelect(element, toSelect);

    if (toSelect != null) {
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(toSelect);
      virtualFile = virtualFile == null? null : BackedVirtualFile.getOriginFileIfBacked(virtualFile);
      select(toSelect, virtualFile, requestFocus);
    }
  }

  private TreeStructureProvider[] getProvidersDumbAware() {
    List<TreeStructureProvider> dumbAware = DumbService.getInstance(myProject).filterByDumbAwareness(TreeStructureProvider.EP.getExtensions(myProject));
    return dumbAware.toArray(new TreeStructureProvider[0]);
  }

  @Override
  public final String getToolWindowId() {
    return ToolWindowId.PROJECT_VIEW;
  }

  public final void setSubId(String subId) {
    mySubId = subId;
  }

  public final String getSubId() {
    return mySubId;
  }
}