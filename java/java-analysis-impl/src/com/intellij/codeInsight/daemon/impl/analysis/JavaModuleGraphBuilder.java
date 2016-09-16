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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.psi.PsiJavaModule.MODULE_INFO_FILE;
import static com.intellij.psi.SyntaxTraverser.psiTraverser;
import static com.intellij.psi.util.PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT;

public class JavaModuleGraphBuilder {
  private JavaModuleGraphBuilder() { }

  @Nullable
  public static Graph<PsiJavaModule> getOrBuild(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      Graph<PsiJavaModule> graph = ReadAction.compute(() -> build(project));
      return Result.create(graph, OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    });
  }

  @Nullable
  public static Collection<PsiJavaModule> findCycle(@NotNull Graph<PsiJavaModule> graph, @NotNull PsiJavaModule module) {
    return ((JavaModuleGraph)graph).myCycles.stream().filter(set -> set.contains(module)).findFirst().orElse(null);
  }

  // Discovers relationships between Java modules in the project.
  // Library/JDK modules are excluded - in assumption there can't be any lib -> src dependencies.
  private static Graph<PsiJavaModule> build(Project project) {
    Set<PsiJavaModule> projectModules = ContainerUtil.newHashSet();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(project, MODULE_INFO_FILE, module.getModuleScope(false));
      if (files.size() > 1) return null;  // aborts the process when there are incorrect modules in the project
      VirtualFile vFile = ContainerUtil.getFirstItem(files);
      if (vFile != null) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
        if (psiFile instanceof PsiJavaFile) {
          PsiJavaModule moduleDeclaration = ((PsiJavaFile)psiFile).getModuleDeclaration();
          if (moduleDeclaration != null) {
            projectModules.add(moduleDeclaration);
          }
        }
      }
    }

    MultiMap<PsiJavaModule, PsiJavaModule> relations = MultiMap.create();
    for (PsiJavaModule moduleDeclaration : projectModules) {
      for (PsiRequiresStatement statement : psiTraverser().children(moduleDeclaration).filter(PsiRequiresStatement.class)) {
        PsiJavaModule dependency = resolveDependency(statement);
        if (dependency != null && projectModules.contains(dependency)) {
          relations.putValue(moduleDeclaration, dependency);
        }
      }
    }
    return new JavaModuleGraph(relations);
  }

  private static PsiJavaModule resolveDependency(PsiRequiresStatement statement) {
    PsiJavaModuleReferenceElement refElement = statement.getReferenceElement();
    if (refElement != null) {
      PsiPolyVariantReference ref = refElement.getReference();
      if (ref != null) {
        ResolveResult[] results = ref.multiResolve(true);
        if (results.length == 1) {
          PsiElement target = results[0].getElement();
          if (target instanceof PsiJavaModule) {
            return (PsiJavaModule)target;
          }
        }
      }
    }

    return null;
  }

  private static class JavaModuleGraph implements Graph<PsiJavaModule> {
    private final MultiMap<PsiJavaModule, PsiJavaModule> myMap;
    private final List<Set<PsiJavaModule>> myCycles;

    public JavaModuleGraph(MultiMap<PsiJavaModule, PsiJavaModule> map) {
      myMap = map;

      DFSTBuilder<PsiJavaModule> builder = new DFSTBuilder<>(this);
      myCycles = builder.getComponents().stream()
        .map(ContainerUtil::newLinkedHashSet)
        .collect(Collectors.toList());
    }

    @Override
    public Collection<PsiJavaModule> getNodes() {
      return myMap.keySet();
    }

    @Override
    public Iterator<PsiJavaModule> getIn(PsiJavaModule n) {
      return ContainerUtil.emptyIterator();
    }

    @Override
    public Iterator<PsiJavaModule> getOut(PsiJavaModule n) {
      return myMap.get(n).iterator();
    }
  }
}