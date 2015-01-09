/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementFinder;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Caches the list of PsiElementFinder instances for a project with support for dumb mode filtering.
 *
 * @author yole
 */
public class PsiElementFinderCache {
  private final Project myProject;
  private volatile PsiElementFinder[] myElementFinders;

  public PsiElementFinderCache(Project project) {
    myProject = project;
  }

  @NotNull
  public PsiElementFinder[] finders() {
    PsiElementFinder[] answer = myElementFinders;
    if (answer == null) {
      answer = calcFinders();
      myElementFinders = answer;
    }

    return answer;
  }

  @NotNull
  protected PsiElementFinder[] calcFinders() {
    List<PsiElementFinder> elementFinders = new ArrayList<PsiElementFinder>();
    ContainerUtil.addAll(elementFinders, myProject.getExtensions(PsiElementFinder.EP_NAME));
    return elementFinders.toArray(new PsiElementFinder[elementFinders.size()]);
  }

  @NotNull
  public PsiElementFinder[] filteredFinders() {
    DumbService dumbService = DumbService.getInstance(myProject);
    PsiElementFinder[] finders = finders();
    if (dumbService.isDumb()) {
      List<PsiElementFinder> list = dumbService.filterByDumbAwareness(Arrays.asList(finders));
      finders = list.toArray(new PsiElementFinder[list.size()]);
    }
    return finders;
  }
}
