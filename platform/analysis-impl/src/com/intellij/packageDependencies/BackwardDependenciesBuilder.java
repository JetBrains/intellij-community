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

package com.intellij.packageDependencies;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public class BackwardDependenciesBuilder extends DependenciesBuilder {
  private final AnalysisScope myForwardScope;
  private final AnalysisScope myScopeOfInterest;

  public BackwardDependenciesBuilder(final Project project, final AnalysisScope scope) {
    this(project, scope, null);
  }

  public BackwardDependenciesBuilder(final Project project, final AnalysisScope scope, final @Nullable AnalysisScope scopeOfInterest) {
    super(project, scope);
    myScopeOfInterest = scopeOfInterest;
    myForwardScope = scopeOfInterest != null
                     ? scopeOfInterest
                     : ReadAction.compute(() -> getScope().getNarrowedComplementaryScope(getProject()));
    myFileCount = myForwardScope.getFileCount();
    myTotalFileCount = myFileCount + scope.getFileCount();
  }

  @Override
  public String getRootNodeNameInUsageView() {
    return AnalysisBundle.message("backward.dependencies.usage.view.root.node.text");
  }

  @Override
  public String getInitialUsagesPosition() {
    return AnalysisBundle.message("backward.dependencies.usage.view.initial.text");
  }

  public AnalysisScope getScopeOfInterest() {
    return myScopeOfInterest;
  }

  @Override
  public boolean isBackward() {
    return true;
  }

  @Override
  public void doAnalyze() {
    final DependenciesBuilder builder = new ForwardDependenciesBuilder(getProject(), myForwardScope);
    builder.setTotalFileCount(myTotalFileCount);
    builder.analyze();

    subtractScope(builder, getScope());
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    psiManager.runInBatchFilesMode(() -> {
      final int fileCount = getScope().getFileCount();
      final boolean includeTestSource = getScope().isIncludeTestSource();
      getScope().accept(virtualFile -> {
        if (!includeTestSource && TestSourcesFilter.isTestSources(virtualFile, getProject())) {
          return true;
        }
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          if (indicator.isCanceled()) {
            throw new ProcessCanceledException();
          }
          indicator.setText(AnalysisBundle.message("package.dependencies.progress.text"));
          indicator.setText2(getRelativeToProjectPath(virtualFile));
          if (fileCount > 0) {
            indicator.setFraction(((double)++myFileCount) / myTotalFileCount);
          }
        }
        ApplicationManager.getApplication().runReadAction(() -> {
          PsiFile file = psiManager.findFile(virtualFile);
          if (file != null) {
            final PsiElement navigationElement = file.getNavigationElement();
            if (navigationElement instanceof PsiFile) {
              file = (PsiFile)navigationElement;
            }
            final Map<PsiFile, Set<PsiFile>> dependencies = builder.getDependencies();
            for (final PsiFile psiFile : dependencies.keySet()) {
              if (dependencies.get(psiFile).contains(file)) {
                Set<PsiFile> fileDeps = getDependencies().computeIfAbsent(file, __ -> new HashSet<>());
                fileDeps.add(psiFile);
              }
            }
            psiManager.dropResolveCaches();
          }
        });
        return true;
      });
      return null;
    });
  }

  private static void subtractScope(final DependenciesBuilder builders, final AnalysisScope scope) {
    final Map<PsiFile, Set<PsiFile>> dependencies = builders.getDependencies();

    Set<PsiFile> excluded = new HashSet<>();

    for (final PsiFile psiFile : dependencies.keySet()) {
      if (scope.contains(psiFile)) {
        excluded.add(psiFile);
      }
    }

    for ( final PsiFile psiFile : excluded ) {
      dependencies.remove(psiFile);
    }
  }

  public AnalysisScope getForwardScope() {
    return myForwardScope;
  }
}
