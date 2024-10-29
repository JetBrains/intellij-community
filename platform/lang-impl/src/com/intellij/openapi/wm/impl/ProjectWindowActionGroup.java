// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.IdeDependentActionGroup;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.ModuleAttachProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Bas Leijdekkers
 */
public final class ProjectWindowActionGroup extends IdeDependentActionGroup implements ActionRemoteBehaviorSpecification.Frontend {
  private ProjectWindowAction latest = null;

  public void addProject(@NotNull Project project) {
    final String projectLocation = project.getPresentableUrl();
    if (projectLocation == null) {
      return;
    }
    final String projectName = getProjectDisplayName(project);
    final ProjectWindowAction windowAction = new ProjectWindowAction(projectName, projectLocation, latest);
    final List<ProjectWindowAction> duplicateWindowActions = findWindowActionsWithProjectName(projectName);
    if (!duplicateWindowActions.isEmpty()) {
      for (ProjectWindowAction action : duplicateWindowActions) {
        action.getTemplatePresentation().setText(FileUtil.getLocationRelativeToUserHome(action.getProjectLocation()));
      }
      windowAction.getTemplatePresentation().setText(FileUtil.getLocationRelativeToUserHome(windowAction.getProjectLocation()));
    }
    add(windowAction);
    latest = windowAction;
  }

  private static @NlsActions.ActionText String getProjectDisplayName(Project project) {
    if (LightEdit.owns(project)) return LightEditService.getWindowName();
    String name = ModuleAttachProcessor.getMultiProjectDisplayName(project);
    return name != null ? name : project.getName();
  }

  public void removeProject(@NotNull Project project) {
    final ProjectWindowAction windowAction = findWindowAction(project.getPresentableUrl());
    if (windowAction == null) {
      return;
    }
    if (latest == windowAction) {
      final ProjectWindowAction previous = latest.getPrevious();
      if (previous != latest) {
        latest = previous;
      } else {
        latest = null;
      }
    }
    remove(windowAction);
    final String projectName = getProjectDisplayName(project);
    final List<ProjectWindowAction> duplicateWindowActions = findWindowActionsWithProjectName(projectName);
    if (duplicateWindowActions.size() == 1) {
      duplicateWindowActions.get(0).getTemplatePresentation().setText(projectName);
    }
    windowAction.dispose();
  }

  public boolean isEnabled() {
    return latest != null && latest.getPrevious() != latest;
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  public void activateNextWindow(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    final ProjectWindowAction windowAction = findWindowAction(project.getPresentableUrl());
    if (windowAction == null) {
      return;
    }
    final ProjectWindowAction next = windowAction.getNext();
    if (next != null) {
      next.setSelected(e, true);
    }
  }

  public void activatePreviousWindow(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    final ProjectWindowAction windowAction = findWindowAction(project.getPresentableUrl());
    if (windowAction == null) {
      return;
    }
    final ProjectWindowAction previous = windowAction.getPrevious();
    if (previous != null) {
      previous.setSelected(e, true);
    }
  }

  private @Nullable ProjectWindowAction findWindowAction(String projectLocation) {
    if (projectLocation == null) {
      return null;
    }
    final AnAction[] children = getChildren(ActionManager.getInstance());
    for (AnAction child : children) {
      if (!(child instanceof ProjectWindowAction windowAction)) {
        continue;
      }
      if (projectLocation.equals(windowAction.getProjectLocation())) {
        return windowAction;
      }
    }
    return null;
  }

  private List<ProjectWindowAction> findWindowActionsWithProjectName(String projectName) {
    List<ProjectWindowAction> result = null;
    final AnAction[] children = getChildren(ActionManager.getInstance());
    for (AnAction child : children) {
      if (!(child instanceof ProjectWindowAction windowAction)) {
        continue;
      }
      if (projectName.equals(windowAction.getProjectName())) {
        if (result == null) {
          result = new ArrayList<>();
        }
        result.add(windowAction);
      }
    }
    if (result == null) {
      return Collections.emptyList();
    }
    return result;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent event) {
    AnAction[] children = super.getChildren(event);
    Arrays.sort(children, SORT_BY_NAME);
    return children;
  }

  private static @Nullable String getProjectName(AnAction action) {
    return action instanceof ProjectWindowAction ? ((ProjectWindowAction)action).getProjectName() : null;
  }

  private static final Comparator<AnAction> SORT_BY_NAME = (action1, action2) -> {
    return StringUtil.naturalCompare(getProjectName(action1), getProjectName(action2));
  };
}
