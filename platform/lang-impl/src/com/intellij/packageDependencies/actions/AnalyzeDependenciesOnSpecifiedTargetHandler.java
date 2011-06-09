/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class AnalyzeDependenciesOnSpecifiedTargetHandler extends DependenciesHandlerBase {
  private final GlobalSearchScope myTargetScope;

  public AnalyzeDependenciesOnSpecifiedTargetHandler(@NotNull Project project, @NotNull AnalysisScope scope, @NotNull GlobalSearchScope targetScope) {
    super(project, Collections.singletonList(scope), Collections.<PsiFile>emptySet());
    myTargetScope = targetScope;
  }

  @Override
  protected String getProgressTitle() {
    return AnalysisScopeBundle.message("package.dependencies.progress.title");
  }

  @Override
  protected String getPanelDisplayName(AnalysisScope scope) {
    return AnalysisScopeBundle.message("package.dependencies.on.toolwindow.title", scope.getDisplayName(), myTargetScope.getDisplayName());
  }

  @Override
  protected DependenciesBuilder createDependenciesBuilder(AnalysisScope scope) {
    return new ForwardDependenciesBuilder(myProject, scope) {
      @Override
      public void analyze() {
        super.analyze();
        final Map<PsiFile,Set<PsiFile>> dependencies = getDependencies();
        for (PsiFile file : dependencies.keySet()) {
          final Set<PsiFile> files = dependencies.get(file);
          final Iterator<PsiFile> iterator = files.iterator();
          while (iterator.hasNext()) {
            PsiFile next = iterator.next();
            final VirtualFile virtualFile = next.getVirtualFile();
            if (virtualFile == null || !myTargetScope.contains(virtualFile)) {
              iterator.remove();
            }
          }
        }
      }
    };
  }
}
