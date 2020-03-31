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

import com.intellij.analysis.AnalysisBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ForwardDependenciesBuilder extends DependenciesBuilder {
  private final Map<PsiFile, Set<PsiFile>> myDirectDependencies = new HashMap<>();
  private final int myTransitive;
  @Nullable
  private final GlobalSearchScope myTargetScope;
  private Set<VirtualFile> myStarted = new THashSet<>();

  public ForwardDependenciesBuilder(@NotNull Project project, @NotNull AnalysisScope scope) {
    super(project, scope);
    myTransitive = 0;
    myTargetScope = null;
  }

  /**
   * Creates builder which reports dependencies on files from {@code targetScope} only.
   */
  public ForwardDependenciesBuilder(@NotNull Project project, @NotNull AnalysisScope scope, @Nullable GlobalSearchScope targetScope) {
    super(project, scope);
    myTargetScope = targetScope;
    myTransitive = 0;
  }

  public ForwardDependenciesBuilder(@NotNull Project project, @NotNull AnalysisScope scope, final int transitive) {
    super(project, scope);
    myTransitive = transitive;
    myTargetScope = null;
  }

  @Override
  public String getRootNodeNameInUsageView(){
    return AnalysisBundle.message("forward.dependencies.usage.view.root.node.text");
  }

  @Override
  public String getInitialUsagesPosition(){
    return AnalysisBundle.message("forward.dependencies.usage.view.initial.text");
  }

  @Override
  public boolean isBackward(){
    return false;
  }

  @Override
  public void analyze() {
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    psiManager.startBatchFilesProcessingMode();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();
    try {
      getScope().acceptIdempotentVisitor(new PsiRecursiveElementVisitor() {
        @Override
        public void visitFile(@NotNull final PsiFile file) {
          visit(file, fileIndex, psiManager);
        }
      });
    }
    finally {
      psiManager.finishBatchFilesProcessingMode();
    }
  }

  private void visit(@NotNull PsiFile file, @NotNull ProjectFileIndex fileIndex, @NotNull PsiManager psiManager) {
    final FileViewProvider viewProvider = file.getViewProvider();
    if (viewProvider.getBaseLanguage() != file.getLanguage()) return;

    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    final VirtualFile virtualFile = file.getVirtualFile();
    if (indicator != null) {
      if (indicator.isCanceled()) {
        throw new ProcessCanceledException();
      }
      indicator.setText(AnalysisBundle.message("package.dependencies.progress.text"));

      if (virtualFile != null) {
        indicator.setText2(getRelativeToProjectPath(virtualFile));
      }
      if ( myTotalFileCount > 0 && myStarted.add(virtualFile)) {
        indicator.setFraction(((double)++ myFileCount) / myTotalFileCount);
      }
    }

    final boolean isInLibrary =  virtualFile == null || fileIndex.isInLibrary(virtualFile);
    final Set<PsiFile> collectedDeps = new HashSet<>();
    collectedDeps.add(file);
    int depth = 0;
    Set<PsiFile> processed = new HashSet<>();
    do {
      if (depth++ > getTransitiveBorder()) return;
      for (PsiFile psiFile : new HashSet<>(collectedDeps)) {
        final VirtualFile vFile = psiFile.getVirtualFile();
        if (vFile != null) {

          if (indicator != null) {
            indicator.setText2(getRelativeToProjectPath(vFile));
          }

          if (!isInLibrary && fileIndex.isInLibrary(vFile)) {
            processed.add(psiFile);
          }
        }
        if (processed.add(psiFile)) {
          Set<PsiFile> found = new HashSet<>();
          analyzeFileDependencies(psiFile, (place, dependency) -> {
            PsiFile dependencyFile = dependency.getContainingFile();
            if (dependencyFile != null) {
              if (viewProvider == dependencyFile.getViewProvider()) return;
              if (dependencyFile.isPhysical()) {
                final VirtualFile depFile = dependencyFile.getVirtualFile();
                if (depFile != null
                    && (fileIndex.isInContent(depFile) || fileIndex.isInLibrary(depFile))
                    && (myTargetScope == null || myTargetScope.contains(depFile))) {
                  final PsiElement navigationElement = dependencyFile.getNavigationElement();
                  found.add(navigationElement instanceof PsiFile ? (PsiFile)navigationElement : dependencyFile);
                }
              }
            }
          });
          Set<PsiFile> deps = getDependencies().computeIfAbsent(file, __ -> new HashSet<>());
          deps.addAll(found);

          getDirectDependencies().put(psiFile, new HashSet<>(found));

          collectedDeps.addAll(found);

          psiManager.dropResolveCaches();
          InjectedLanguageManager.getInstance(file.getProject()).dropFileCaches(psiFile);
        }
      }
      collectedDeps.removeAll(processed);
    }
    while (isTransitive() && !collectedDeps.isEmpty());
  }

  @NotNull
  @Override
  public Map<PsiFile, Set<PsiFile>> getDirectDependencies() {
    return myDirectDependencies;
  }

  @Override
  public boolean isTransitive() {
    return myTransitive > 0;
  }

  @Override
  public int getTransitiveBorder() {
    return myTransitive;
  }
}
