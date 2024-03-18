// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service(Service.Level.PROJECT)
public final class SelectInManager  {
  private final Project myProject;
  /**
   * @deprecated Use {@link #getProject()} instead
   */
  @Deprecated(forRemoval = true) public static final @NonNls String PROJECT = getProject();

  public SelectInManager(@NotNull Project project) {
    myProject = project;
  }

  public @NotNull List<SelectInTarget> getTargetList() {
    List<SelectInTarget> targets = new ArrayList<>(DumbService.getDumbAwareExtensions(myProject, SelectInTarget.EP_NAME));
    targets.sort(SelectInTargetComparator.INSTANCE);
    return targets;
  }

  /**
   * @deprecated Use {@link #getTargetList()}
   */
  @Deprecated
  public SelectInTarget @NotNull [] getTargets() {
    return getTargetList().toArray(new SelectInTarget[0]);
  }

  public static SelectInManager getInstance(@NotNull Project project) {
    return project.getService(SelectInManager.class);
  }

  public static SelectInTarget findSelectInTarget(@NotNull String id, Project project) {
    SelectInManager manager = project == null || project.isDisposed() ? null : getInstance(project);
    List<SelectInTarget> targets = manager == null ? null : manager.getTargetList();
    if (targets != null) {
      for (SelectInTarget target : targets) {
        if (target != null && Objects.equals(id, target.getToolWindowId())) {
          return target;
        }
      }
    }
    return null;
  }

  public static final class SelectInTargetComparator implements Comparator<SelectInTarget> {
    public static final Comparator<SelectInTarget> INSTANCE = new SelectInTargetComparator();

    @Override
    public int compare(final SelectInTarget o1, final SelectInTarget o2) {
      return Float.compare(o1.getWeight(), o2.getWeight());
    }
  }

  public static @Nls String getProject() {
    return IdeBundle.message("select.in.project");
  }
}
