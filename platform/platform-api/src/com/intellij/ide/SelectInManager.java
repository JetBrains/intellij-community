// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

public class SelectInManager  {
  private final Project myProject;
  private final List<SelectInTarget> myTargets = new ArrayList<>();
  private boolean myLoadedExtensions = false;
  @NonNls public static final String PROJECT = IdeBundle.message("select.in.project");
  @NonNls public static final String PACKAGES = IdeBundle.message("select.in.packages");
  @NonNls public static final String ASPECTS = IdeBundle.message("select.in.aspects");
  @NonNls public static final String COMMANDER = IdeBundle.message("select.in.commander");
  @NonNls public static final String FAVORITES = IdeBundle.message("select.in.favorites");
  @NonNls public static final String NAV_BAR = IdeBundle.message("select.in.nav.bar");
  @NonNls public static final String SCOPE = IdeBundle.message("select.in.scope");

  public SelectInManager(final Project project) {
    myProject = project;
  }

  /**
   * "Select In" targets should be registered as extension points ({@link SelectInTarget#EP_NAME}).
   */
  @Deprecated
  public void addTarget(SelectInTarget target) {
    myTargets.add(target);
  }

  public void removeTarget(SelectInTarget target) {
    myTargets.remove(target);
  }

  public SelectInTarget[] getTargets() {
    checkLoadExtensions();
    Stream<SelectInTarget> stream = myTargets.stream();
    if (DumbService.getInstance(myProject).isDumb()) {
      stream = stream.filter(target -> DumbService.isDumbAware(target));
    }
    return stream.sorted(SelectInTargetComparator.INSTANCE).toArray(SelectInTarget[]::new);
  }

  private void checkLoadExtensions() {
    if (!myLoadedExtensions) {
      myLoadedExtensions = true;
      Collections.addAll(myTargets, Extensions.getExtensions(SelectInTarget.EP_NAME, myProject));
    }
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

    public int compare(final SelectInTarget o1, final SelectInTarget o2) {
      return Float.compare(o1.getWeight(), o2.getWeight());
    }
  }
}
