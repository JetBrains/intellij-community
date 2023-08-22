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
package com.intellij.packageDependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtilCore;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiFileEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author anna
 */
public abstract class DependenciesBuilder {
  private final Project myProject;
  private final AnalysisScope myScope;
  private final Map<PsiFile, Set<PsiFile>> myDependencies = new HashMap<>();
  protected int myTotalFileCount;
  protected int myFileCount = 0;

  protected DependenciesBuilder(@NotNull final Project project, @NotNull final AnalysisScope scope) {
    myProject = project;
    myScope = scope;
    myTotalFileCount = scope.getFileCount();
  }

  public void setInitialFileCount(final int fileCount) {
    myFileCount = fileCount;
  }

  public void setTotalFileCount(final int totalFileCount) {
    myTotalFileCount = totalFileCount;
  }

  @NotNull
  public Map<PsiFile, Set<PsiFile>> getDependencies() {
    return myDependencies;
  }

  @NotNull
  public Map<PsiFile, Set<PsiFile>> getDirectDependencies() {
    return getDependencies();
  }

  @NotNull
  public AnalysisScope getScope() {
    return myScope;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public abstract @Nls String getRootNodeNameInUsageView();

  public abstract @Nls String getInitialUsagesPosition();

  public abstract boolean isBackward();

  public abstract void analyze();

  public Map<PsiFile, Map<DependencyRule, Set<PsiFile>>> getIllegalDependencies(){
    Map<PsiFile, Map<DependencyRule, Set<PsiFile>>> result = new HashMap<>();
    DependencyValidationManager validator = DependencyValidationManager.getInstance(myProject);
    for (PsiFile file : getDirectDependencies().keySet()) {
      Set<PsiFile> deps = getDirectDependencies().get(file);
      Map<DependencyRule, Set<PsiFile>> illegal = null;
      for (PsiFile dependency : deps) {
        final DependencyRule rule = isBackward() ?
                                    validator.getViolatorDependencyRule(dependency, file) :
                                    validator.getViolatorDependencyRule(file, dependency);
        if (rule != null) {
          if (illegal == null) {
            illegal = new HashMap<>();
            result.put(file, illegal);
          }
          Set<PsiFile> illegalFilesByRule = illegal.get(rule);
          if (illegalFilesByRule == null) {
            illegalFilesByRule = new HashSet<>();
          }
          illegalFilesByRule.add(dependency);
          illegal.put(rule, illegalFilesByRule);
        }
      }
    }
    return result;
  }

  public List<List<PsiFile>> findPaths(PsiFile from, PsiFile to) {
    return findPaths(from, to, new HashSet<>());
  }

  private List<List<PsiFile>> findPaths(PsiFile from, PsiFile to, Set<? super PsiFile> processed) {
    final List<List<PsiFile>> result = new ArrayList<>();
    final Set<PsiFile> reachable = getDirectDependencies().get(from);
    if (reachable != null) {
      if (reachable.contains(to)) {
        result.add(new ArrayList<>());
        return result;
      }
      if (processed.add(from)) {
        for (PsiFile file : reachable) {
          if (!getScope().contains(file)) { //exclude paths through scope
            final List<List<PsiFile>> paths = findPaths(file, to, processed);
            for (List<PsiFile> path : paths) {
              path.add(0, file);
            }
            result.addAll(paths);
          }
        }
      }
    }
    return result;
  }

  @NlsSafe String getRelativeToProjectPath(@NotNull VirtualFile virtualFile) {
    return ProjectUtilCore.displayUrlRelativeToProject(virtualFile, virtualFile.getPresentableUrl(), getProject(), true, false);
  }

  public static void analyzeFileDependencies(@NotNull PsiFile file, @NotNull DependencyProcessor processor) {
    analyzeFileDependencies(file, processor, DependencyVisitorFactory.VisitorOptions.fromSettings(file.getProject()));
  }

  public static void analyzeFileDependencies(@NotNull PsiFile file,
                                             @NotNull DependencyProcessor processor,
                                             @NotNull DependencyVisitorFactory.VisitorOptions options) {
    Boolean prev = file.getUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING);
    file.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, Boolean.TRUE);
    try {
      file.accept(DependencyVisitorFactory.createVisitor(file, processor, options));
    }
    finally {
      file.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, prev);
    }
  }

  @FunctionalInterface
  public interface DependencyProcessor {
    void process(@NotNull PsiElement place, @NotNull PsiElement dependency);
  }
}
