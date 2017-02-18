/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.*;

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
   * "Select In" targets should be registered as extension points ({@link com.intellij.ide.SelectInTarget#EP_NAME}).
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
    SelectInTarget[] targets = myTargets.toArray(new SelectInTarget[myTargets.size()]);
    Arrays.sort(targets, new SelectInTargetComparator());

    if (DumbService.getInstance(myProject).isDumb()) {
      final List<SelectInTarget> awareList = (List)ContainerUtil.findAll(targets, DumbAware.class);
      return awareList.toArray(new SelectInTarget[awareList.size()]);
    }

    return targets;
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

  public static class SelectInTargetComparator implements Comparator<SelectInTarget> {
    public int compare(final SelectInTarget o1, final SelectInTarget o2) {
      if (o1.getWeight() < o2.getWeight()) return -1;
      if (o1.getWeight() > o2.getWeight()) return 1;
      return 0;
    }
  }
}
