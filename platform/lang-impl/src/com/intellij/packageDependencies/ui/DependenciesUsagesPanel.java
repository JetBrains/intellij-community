/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.packageDependencies.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.FindDependencyUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usageView.UsageInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class DependenciesUsagesPanel extends UsagesPanel {
  private final List<DependenciesBuilder> myBuilders;

  public DependenciesUsagesPanel(Project project, final List<DependenciesBuilder> builders) {
    super(project);
    myBuilders = builders;
    setToInitialPosition();
  }

  @Override
  public String getInitialPositionText() {
    return myBuilders.get(0).getInitialUsagesPosition();
  }


  @Override
  public String getCodeUsagesString() {
    return myBuilders.get(0).getRootNodeNameInUsageView();
  }

  public void findUsages(final Set<PsiFile> searchIn, final Set<PsiFile> searchFor) {
    cancelCurrentFindRequest();

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final ProgressIndicator progress = new PanelProgressIndicator(component -> setToComponent(component));
      myCurrentProgress = progress;
      ProgressManager.getInstance().runProcess(() -> {
        FoundUsages found;
        try {
          // The search can go through every file of a large scope. A non-blocking read action restarts when a write action
          // is pending, so it does not block typing for the whole search.
          found = ReadAction.nonBlocking(() -> findUsagesInReadAction(searchIn, searchFor))
            .expireWith(this)
            .executeSynchronously();
        }
        catch (ProcessCanceledException e) {
          // A newer request or the disposal of the panel canceled the search: nothing to show.
          found = null;
        }
        catch (Exception e) {
          LOG.error(e);
          found = new FoundUsages(PsiElement.EMPTY_ARRAY, UsageInfo.EMPTY_ARRAY);
        }

        if (found != null && !progress.isCanceled()) {
          final FoundUsages result = found;
          ApplicationManager.getApplication()
            .invokeLater(() -> showUsages(result.elementsToSearch(), result.usages()), ModalityState.stateForComponent(
              this));
        }
        myCurrentProgress = null;
      }, progress);
    }), 300);
  }

  private @NotNull FoundUsages findUsagesInReadAction(@NotNull Set<PsiFile> searchIn, @NotNull Set<PsiFile> searchFor) {
    final Set<PsiFile> elementsToSearch;
    final UsageInfo[] usages;
    if (myBuilders.get(0).isBackward()) {
      elementsToSearch = searchIn;
      usages = FindDependencyUtil.findBackwardDependencies(myBuilders, searchFor, searchIn);
    }
    else {
      elementsToSearch = searchFor;
      usages = FindDependencyUtil.findDependencies(myBuilders, searchIn, searchFor);
    }
    assert !new HashSet<>(elementsToSearch).contains(null);
    return new FoundUsages(PsiUtilCore.toPsiElementArray(elementsToSearch), usages);
  }

  private record FoundUsages(PsiElement @NotNull [] elementsToSearch, UsageInfo @NotNull [] usages) {
  }

  public void addBuilder(DependenciesBuilder builder) {
    myBuilders.add(builder);
  }
}
