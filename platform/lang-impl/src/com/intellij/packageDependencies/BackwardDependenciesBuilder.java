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

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: anna
 * Date: Jan 16, 2005
 */
public class BackwardDependenciesBuilder extends DependenciesBuilder {
  private final AnalysisScope myForwardScope;

  public BackwardDependenciesBuilder(final Project project, final AnalysisScope scope) {
    this(project, scope, null);
  }

  public BackwardDependenciesBuilder(final Project project, final AnalysisScope scope, final @Nullable AnalysisScope scopeOfInterest) {
    super(project, scope, scopeOfInterest);
    myForwardScope = getScope().getNarrowedComplementaryScope(getProject());
    myFileCount = myForwardScope.getFileCount();
    myTotalFileCount = myFileCount + scope.getFileCount();
  }

  public String getRootNodeNameInUsageView() {
    return AnalysisScopeBundle.message("backward.dependencies.usage.view.root.node.text");
  }

  public String getInitialUsagesPosition() {
    return AnalysisScopeBundle.message("backward.dependencies.usage.view.initial.text");
  }

  public boolean isBackward() {
    return true;
  }

  public void analyze() {
    AnalysisScope scope = myForwardScope;
    final DependenciesBuilder builder = new ForwardDependenciesBuilder(getProject(), scope, getScopeOfInterest());
    builder.setTotalFileCount(myTotalFileCount);
    builder.analyze();

    subtractScope(builder, getScope());
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    psiManager.startBatchFilesProcessingMode();
    try {
      final int fileCount = getScope().getFileCount();
      getScope().accept(new PsiRecursiveElementVisitor() {
        @Override public void visitFile(final PsiFile file) {
          ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          if (indicator != null) {
            if (indicator.isCanceled()) {
              throw new ProcessCanceledException();
            }
            indicator.setText(AnalysisScopeBundle.message("package.dependencies.progress.text"));
            final VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile != null) {
              indicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, getProject()));
            }
            if (fileCount > 0) {
              indicator.setFraction(((double)++myFileCount) / myTotalFileCount);
            }
          }
          final Map<PsiFile, Set<PsiFile>> dependencies = builder.getDependencies();
          for (final PsiFile psiFile : dependencies.keySet()) {
            if (dependencies.get(psiFile).contains(file)) {
              Set<PsiFile> fileDeps = getDependencies().get(file);
              if (fileDeps == null) {
                fileDeps = new HashSet<PsiFile>();
                getDependencies().put(file, fileDeps);
              }
              fileDeps.add(psiFile);
            }
          }
          psiManager.dropResolveCaches();
          psiManager.dropFileCaches(file);
        }
      });
    }
    finally {
      psiManager.finishBatchFilesProcessingMode();
    }
  }

  private static void subtractScope(final DependenciesBuilder builders, final AnalysisScope scope) {
    final Map<PsiFile, Set<PsiFile>> dependencies = builders.getDependencies();

    Set<PsiFile> excluded = new HashSet<PsiFile>();

    for (final PsiFile psiFile : dependencies.keySet()) {
      if (scope.contains(psiFile)) {
        excluded.add(psiFile);
      }
    }

    for ( final PsiFile psiFile : excluded ) {
      dependencies.remove(psiFile);
    }
  }
}
