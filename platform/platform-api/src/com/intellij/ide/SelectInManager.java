// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.SimpleSmartExtensionPoint;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class SelectInManager  {
  private final Project myProject;
  private final SimpleSmartExtensionPoint<SelectInTarget> myTargets;
  @NonNls public static final String PROJECT = IdeBundle.message("select.in.project");
  @NonNls public static final String PACKAGES = IdeBundle.message("select.in.packages");
  @NonNls public static final String COMMANDER = IdeBundle.message("select.in.commander");
  @NonNls public static final String FAVORITES = IdeBundle.message("select.in.favorites");
  @NonNls public static final String NAV_BAR = IdeBundle.message("select.in.nav.bar");
  @NonNls public static final String SCOPE = IdeBundle.message("select.in.scope");

  public SelectInManager(final Project project) {
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

  public SelectInTarget[] getTargets() {
    List<SelectInTarget> targets = myTargets.getExtensions();
    if (DumbService.getInstance(myProject).isDumb()) {
      targets = ContainerUtil.filter(targets, target -> DumbService.isDumbAware(target));
    }
    return ContainerUtil.sorted(targets, SelectInTargetComparator.INSTANCE).toArray(new SelectInTarget[0]);
  }

  public static SelectInManager getInstance(Project project) {
    return ServiceManager.getService(project, SelectInManager.class);
  }

  public static SelectInTarget findSelectInTarget(@NotNull String id, Project project) {
    SelectInManager manager = project == null || project.isDisposed() ? null : SelectInManager.getInstance(project);
    SelectInTarget[] targets = manager == null ? null : manager.getTargets();
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
}
