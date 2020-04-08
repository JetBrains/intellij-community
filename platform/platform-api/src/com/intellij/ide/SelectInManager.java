// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.extensions.SimpleSmartExtensionPoint;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SelectInManager  {
  private final Project myProject;
  private final SimpleSmartExtensionPoint<SelectInTarget> myTargets;
  /**
   * @deprecated Use {@link #getProject()} instead
   */
  @Deprecated
  @NonNls public static final String PROJECT = getProject();

  public SelectInManager(@NotNull Project project) {
    myProject = project;
    myTargets = SimpleSmartExtensionPoint.create(myProject.getExtensionArea(), SelectInTarget.EP_NAME);
  }

  /**
   * @deprecated targets should be registered as extension points ({@link SelectInTarget#EP_NAME}).
   */
  @Deprecated
  public void addTarget(SelectInTarget target) {
    myTargets.addExplicitExtension(target);
  }

  public void removeTarget(SelectInTarget target) {
    myTargets.removeExplicitExtension(target);
  }

  @NotNull
  public List<SelectInTarget> getTargetList() {
    List<SelectInTarget> targets = new ArrayList<>(myTargets.getExtensions());
    if (DumbService.getInstance(myProject).isDumb()) {
      targets.removeIf(target -> !DumbService.isDumbAware(target));
    }
    targets.sort(SelectInTargetComparator.INSTANCE);
    return targets;
  }

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

  public static class SelectInTargetComparator implements Comparator<SelectInTarget> {
    public static final Comparator<SelectInTarget> INSTANCE = new SelectInTargetComparator();

    @Override
    public int compare(final SelectInTarget o1, final SelectInTarget o2) {
      return Float.compare(o1.getWeight(), o2.getWeight());
    }
  }

  public static String getProject() {
    return IdeBundle.message("select.in.project");
  }

  public static String getPackages() {
    return IdeBundle.message("select.in.packages");
  }

  public static String getCommander() {
    return IdeBundle.message("select.in.commander");
  }

  public static String getFavorites() {
    return IdeBundle.message("select.in.favorites");
  }

  public static String getNavBar() {
    return IdeBundle.message("select.in.nav.bar");
  }

  public static String getScope() {
    return IdeBundle.message("select.in.scope");
  }
}
