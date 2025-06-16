// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cyclicDependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.psi.*;
import com.intellij.util.graph.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CyclicDependenciesBuilder{
  private final @NotNull Project myProject;
  private final AnalysisScope myScope;
  private final Map<String, PsiPackage> myPackages = new HashMap<>();
  private Graph<PsiPackage> myGraph;
  private final Map<PsiPackage, Map<PsiPackage, Set<PsiFile>>> myFilesInDependentPackages = new HashMap<>();
  private final Map<PsiPackage, Map<PsiPackage, Set<PsiFile>>> myBackwardFilesInDependentPackages = new HashMap<>();
  private final Map<PsiPackage, Set<PsiPackage>> myPackageDependencies = new HashMap<>();
  private HashMap<PsiPackage, Set<List<PsiPackage>>> myCyclicDependencies = new HashMap<>();
  private int myFileCount;
  private final ForwardDependenciesBuilder myForwardBuilder;

  private @Nls String myRootNodeNameInUsageView;

  public CyclicDependenciesBuilder(@NotNull Project project, @NotNull AnalysisScope scope) {
    myProject = project;
    myScope = scope;
    myForwardBuilder = new ForwardDependenciesBuilder(myProject, myScope){
      @Override
      public String getRootNodeNameInUsageView() {
        return CyclicDependenciesBuilder.this.getRootNodeNameInUsageView();
      }

      @Override
      public String getInitialUsagesPosition() {
        return JavaBundle.message("cyclic.dependencies.usage.view.initial.text");
      }
    };
  }

  private @NotNull @Nls String getRootNodeNameInUsageView() {
    return myRootNodeNameInUsageView;
  }

  public void setRootNodeNameInUsageView(@NotNull @Nls String rootNodeNameInUsageView) {
    myRootNodeNameInUsageView = rootNodeNameInUsageView;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public @NotNull AnalysisScope getScope() {
    return myScope;
  }

  public @NotNull DependenciesBuilder getForwardBuilder() {
    return myForwardBuilder;
  }

  public void analyze() {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();
    getScope().accept(new PsiRecursiveElementVisitor() {
      @Override public void visitFile(@NotNull PsiFile file) {
        if (file instanceof PsiJavaFile psiJavaFile) {
          if (getScope().contains(psiJavaFile)) {
            final PsiPackage aPackage = findPackage(psiJavaFile.getPackageName());
            if (aPackage != null) {
              myPackages.put(psiJavaFile.getPackageName(), aPackage);
            }
          }
          final Set<PsiPackage> packs = getPackageHierarchy(psiJavaFile.getPackageName());
          final ForwardDependenciesBuilder builder = new ForwardDependenciesBuilder(getProject(), new AnalysisScope(psiJavaFile));
          builder.setTotalFileCount(getScope().getFileCount());
          builder.setInitialFileCount(++myFileCount);
          builder.analyze();
          final Set<PsiFile> psiFiles = builder.getDependencies().get(psiJavaFile);
          if (psiFiles == null) return;
          for (PsiPackage pack : packs) {
            Set<PsiPackage> pack2Packages = myPackageDependencies.computeIfAbsent(pack, __ -> new HashSet<>());
            for (PsiFile psiFile : psiFiles) {
              if (!(psiFile instanceof PsiJavaFile) ||
                  !projectFileIndex.isInSourceContent(psiFile.getVirtualFile()) ||
                  !getScope().contains(psiFile)) {
                continue;
              }

              // construct dependent packages
              final String packageName = ((PsiJavaFile)psiFile).getPackageName();
              //do not depend on parent packages
              if (packageName.startsWith(pack.getQualifiedName())) {
                continue;
              }
              final PsiPackage depPackage = findPackage(packageName);
              if (depPackage == null) { //not from analyze scope
                continue;
              }
              pack2Packages.add(depPackage);

              constractFilesInDependenciesPackagesMap(pack, depPackage, psiFile, myFilesInDependentPackages);
              constractFilesInDependenciesPackagesMap(depPackage, pack, psiJavaFile, myBackwardFilesInDependentPackages);
              constractWholeDependenciesMap(psiJavaFile, psiFile);
            }
          }
        }
      }
    });
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(indicator);
      indicator.setText(JavaBundle.message("cyclic.dependencies.progress.text"));
      indicator.setText2("");
      indicator.setIndeterminate(true);
    }
    myCyclicDependencies = getCycles(myPackages.values());
  }

  private static void constractFilesInDependenciesPackagesMap(final PsiPackage pack,
                                                              final PsiPackage depPackage,
                                                              final PsiFile file,
                                                              final Map<PsiPackage, Map<PsiPackage, Set<PsiFile>>> filesInDependentPackages) {
    Map<PsiPackage, Set<PsiFile>> dependentPackages2Files = filesInDependentPackages.get(pack);
    if (dependentPackages2Files == null) {
      dependentPackages2Files = new HashMap<>();
      filesInDependentPackages.put(pack, dependentPackages2Files);
    }
    Set<PsiFile> depFiles = dependentPackages2Files.get(depPackage);
    if (depFiles == null) {
      depFiles = new HashSet<>();
      dependentPackages2Files.put(depPackage, depFiles);
    }
    depFiles.add(file);
  }

//construct all dependencies for usage view
  private void constractWholeDependenciesMap(final PsiJavaFile psiJavaFile, final PsiFile psiFile) {
    Set<PsiFile> wholeDependencies = myForwardBuilder.getDependencies().get(psiJavaFile);
    if (wholeDependencies == null) {
      wholeDependencies = new HashSet<>();
      myForwardBuilder.getDependencies().put(psiJavaFile, wholeDependencies);
    }
    wholeDependencies.add(psiFile);
  }

  public Set<PsiFile> getDependentFilesInPackage(PsiPackage pack, PsiPackage depPack) {
    Set<PsiFile> psiFiles = new HashSet<>();
    final Map<PsiPackage, Set<PsiFile>> map = myFilesInDependentPackages.get(pack);
    if (map != null){
      psiFiles = map.get(depPack);
    }
    if (psiFiles == null) {
      psiFiles = new HashSet<>();
    }
    return psiFiles;
  }

  public Set<PsiFile> getDependentFilesInPackage(PsiPackage firstPack, PsiPackage middlePack, PsiPackage lastPack) {
    Set<PsiFile> result = new HashSet<>();
    final Map<PsiPackage, Set<PsiFile>> forwardMap = myFilesInDependentPackages.get(firstPack);
    if (forwardMap != null && forwardMap.get(middlePack) != null){
      result.addAll(forwardMap.get(middlePack));
    }
    final Map<PsiPackage, Set<PsiFile>> backwardMap = myBackwardFilesInDependentPackages.get(lastPack);
    if (backwardMap != null && backwardMap.get(middlePack) != null){
      result.addAll(backwardMap.get(middlePack));
    }
    return result;
  }


  public HashMap<PsiPackage, Set<List<PsiPackage>>> getCyclicDependencies() {
    return myCyclicDependencies;
  }

  public HashMap<PsiPackage, Set<List<PsiPackage>>> getCycles(Collection<? extends PsiPackage> packages) {
    if (myGraph == null){
      myGraph = buildGraph();
    }
    final HashMap<PsiPackage, Set<List<PsiPackage>>> result = new HashMap<>();
    for (PsiPackage psiPackage : packages) {
      Set<List<PsiPackage>> paths2Pack = result.computeIfAbsent(psiPackage, __ -> new HashSet<>());
      paths2Pack.addAll(GraphAlgorithms.getInstance().findCycles(myGraph, psiPackage));
    }
    return result;
  }

  public Map<String, PsiPackage> getAllScopePackages() {
    if (myPackages.isEmpty()) {
      final PsiManager psiManager = PsiManager.getInstance(getProject());
      getScope().accept(new PsiRecursiveElementVisitor() {
        @Override public void visitFile(@NotNull PsiFile psiFile) {
          if (psiFile instanceof PsiJavaFile psiJavaFile) {
            final PsiPackage aPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(psiJavaFile.getPackageName());
            if (aPackage != null) {
              myPackages.put(aPackage.getQualifiedName(), aPackage);
            }
          }
        }
      });
    }
    return myPackages;
  }

  private Graph<PsiPackage> buildGraph() {
    return GraphGenerator.generate(CachingSemiGraph.cache(new InboundSemiGraph<>() {
      @Override
      public @NotNull Collection<PsiPackage> getNodes() {
        return getAllScopePackages().values();
      }

      @Override
      public @NotNull Iterator<PsiPackage> getIn(PsiPackage psiPack) {
        final Set<PsiPackage> psiPackages = myPackageDependencies.get(psiPack);
        if (psiPackages == null) {     //for packs without java classes
          return Collections.emptyIterator();
        }
        return psiPackages.iterator();
      }
    }));
  }

  private @NotNull Set<PsiPackage> getPackageHierarchy(@NotNull String packageName) {
    final Set<PsiPackage> result = new HashSet<>();
    PsiPackage psiPackage = findPackage(packageName);
    if (psiPackage != null) {
      result.add(psiPackage);
    }
    else {
      return result;
    }
    while (psiPackage.getParentPackage() != null && !psiPackage.getParentPackage().getQualifiedName().isEmpty()) {
      final PsiPackage aPackage = findPackage(psiPackage.getParentPackage().getQualifiedName());
      if (aPackage == null) {
        break;
      }
      result.add(aPackage);
      psiPackage = psiPackage.getParentPackage();
    }
    return result;
  }

  private PsiPackage findPackage(@NotNull String packName) {
    return getAllScopePackages().get(packName);
  }
}