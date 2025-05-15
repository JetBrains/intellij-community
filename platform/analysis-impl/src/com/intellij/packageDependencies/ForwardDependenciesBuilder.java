// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ForwardDependenciesBuilder extends DependenciesBuilder {
  private final Map<PsiFile, Set<PsiFile>> myDirectDependencies = new HashMap<>();
  private final int myTransitive;
  private final @Nullable GlobalSearchScope myTargetScope;
  private final Set<VirtualFile> myStarted = new HashSet<>();

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
  public void doAnalyze() {
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    psiManager.runInBatchFilesMode(() -> {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();
      getScope().acceptIdempotentVisitor(new PsiRecursiveElementVisitor() {
        @Override
        public void visitFile(final @NotNull PsiFile psiFile) {
          visit(psiFile, fileIndex, psiManager);
        }
      });
      return null;
    });
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
              final VirtualFile depFile = dependencyFile.getVirtualFile();
              if (depFile != null
                  && (fileIndex.isInContent(depFile) || fileIndex.isInLibrary(depFile))
                  && (myTargetScope == null || myTargetScope.contains(depFile))) {
                final PsiElement navigationElement = dependencyFile.getNavigationElement();
                PsiFile navigationFile = navigationElement instanceof PsiFile ? (PsiFile)navigationElement : dependencyFile;
                if (navigationFile.isPhysical()) {
                  found.add(navigationFile);
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

  @Override
  public @NotNull Map<PsiFile, Set<PsiFile>> getDirectDependencies() {
    return myDirectDependencies;
  }

  private boolean isTransitive() {
    return myTransitive > 0;
  }

  public int getTransitiveBorder() {
    return myTransitive;
  }
}
