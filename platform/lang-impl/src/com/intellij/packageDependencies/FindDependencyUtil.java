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

package com.intellij.packageDependencies;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FindDependencyUtil {
  private FindDependencyUtil() {}

  public static UsageInfo[] findDependencies(@Nullable final List<DependenciesBuilder> builders, Set<PsiFile> searchIn, Set<PsiFile> searchFor) {
    final List<UsageInfo> usages = new ArrayList<UsageInfo>();
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    int totalCount = searchIn.size();
    int count = 0;

    nextFile: for (final PsiFile psiFile : searchIn) {
      count = updateIndicator(indicator, totalCount, count, psiFile);

      if (!psiFile.isValid()) continue;

      final Set<PsiFile> precomputedDeps;
      if (builders != null) {
        final Set<PsiFile> depsByFile = new HashSet<PsiFile>();
        for (DependenciesBuilder builder : builders) {
          final Set<PsiFile> deps = builder.getDependencies().get(psiFile);
          if (deps != null) {
            depsByFile.addAll(deps);
          }
        }
        precomputedDeps = new HashSet<PsiFile>(depsByFile);
        precomputedDeps.retainAll(searchFor);
        if (precomputedDeps.isEmpty()) continue nextFile;
      }
      else {
        precomputedDeps = Collections.unmodifiableSet(searchFor);
      }

      DependenciesBuilder.analyzeFileDependencies(psiFile, new DependenciesBuilder.DependencyProcessor() {
        @Override
        public void process(PsiElement place, PsiElement dependency) {
          PsiFile dependencyFile = dependency.getContainingFile();
          if (precomputedDeps.contains(dependencyFile)) {
            usages.add(new UsageInfo(place));
          }
        }
      });
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  public static UsageInfo[] findBackwardDependencies(final List<DependenciesBuilder> builders, final Set<PsiFile> searchIn, final Set<PsiFile> searchFor) {
    final List<UsageInfo> usages = new ArrayList<UsageInfo>();
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();


    final Set<PsiFile> deps = new HashSet<PsiFile>();
    for (PsiFile psiFile : searchFor) {
      for (DependenciesBuilder builder : builders) {
        final Set<PsiFile> depsByBuilder = builder.getDependencies().get(psiFile);
        if (depsByBuilder != null) {
          deps.addAll(depsByBuilder);
        }
      }
    }
    deps.retainAll(searchIn);
    if (deps.isEmpty()) return new UsageInfo[0];

    int totalCount = deps.size();
    int count = 0;
    for (final PsiFile psiFile : deps) {
      count = updateIndicator(indicator, totalCount, count, psiFile);

      DependenciesBuilder.analyzeFileDependencies(psiFile, new DependenciesBuilder.DependencyProcessor() {
        @Override
        public void process(PsiElement place, PsiElement dependency) {
          PsiFile dependencyFile = dependency.getContainingFile();
          if (searchFor.contains(dependencyFile)) {
            usages.add(new UsageInfo(place));
          }
        }
      });
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  private static int updateIndicator(final ProgressIndicator indicator, final int totalCount, int count, final PsiFile psiFile) {
    if (indicator != null) {
      if (indicator.isCanceled()) throw new ProcessCanceledException();
      indicator.setFraction(((double)++count) / totalCount);
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        indicator.setText(AnalysisScopeBundle.message("find.dependencies.progress.text", virtualFile.getPresentableUrl()));
      }
    }
    return count;
  }

  public static UsageInfo[] findDependencies(final DependenciesBuilder builder, final Set<PsiFile> searchIn, final Set<PsiFile> searchFor) {
    return findDependencies(Collections.singletonList(builder), searchIn, searchFor);
  }

  public static UsageInfo[] findBackwardDependencies(final DependenciesBuilder builder, final Set<PsiFile> searchIn, final Set<PsiFile> searchFor) {
    return findBackwardDependencies(Collections.singletonList(builder), searchIn, searchFor);
  }
}