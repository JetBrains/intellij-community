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

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.psi.PsiJavaModule.MODULE_INFO_FILE;
import static com.intellij.psi.SyntaxTraverser.psiTraverser;
import static com.intellij.psi.util.PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT;

public class JavaModuleGraphUtil {
  private JavaModuleGraphUtil() { }

  @Nullable
  public static Collection<PsiJavaModule> findCycle(@NotNull PsiJavaModule module) {
    Project project = module.getProject();
    List<Set<PsiJavaModule>> cycles = CachedValuesManager.getManager(project).getCachedValue(project, () ->
      Result.create(ReadAction.compute(() -> findCycles(project)), OUT_OF_CODE_BLOCK_MODIFICATION_COUNT));
    return ContainerUtil.find(cycles, set -> set.contains(module));
  }

  // Looks for cycles between Java modules in the project sources.
  // Library/JDK modules are excluded - in assumption there can't be any lib -> src dependencies.
  // Module references are resolved "globally" (i.e., without taking project dependencies into account).
  private static List<Set<PsiJavaModule>> findCycles(Project project) {
    Set<PsiJavaModule> projectModules = ContainerUtil.newHashSet();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(project, MODULE_INFO_FILE, module.getModuleScope(false));
      if (files.size() > 1) return Collections.emptyList();  // aborts the process when there are incorrect modules in the project
      Optional.ofNullable(ContainerUtil.getFirstItem(files))
        .map(PsiManager.getInstance(project)::findFile)
        .map(f -> f instanceof PsiJavaFile ? ((PsiJavaFile)f).getModuleDeclaration() : null)
        .ifPresent(projectModules::add);
    }

    if (!projectModules.isEmpty()) {
      MultiMap<PsiJavaModule, PsiJavaModule> relations = MultiMap.create();
      for (PsiJavaModule moduleDeclaration : projectModules) {
        for (PsiRequiresStatement statement : psiTraverser().children(moduleDeclaration).filter(PsiRequiresStatement.class)) {
          Optional.ofNullable(statement.getReferenceElement())
            .map(PsiJavaModuleReferenceElement::getReference)
            .map(ref -> ref.multiResolve(true))
            .map(a -> a.length == 1 ? a[0].getElement() : null)
            .map(e -> e instanceof PsiJavaModule ? (PsiJavaModule)e : null)
            .filter(projectModules::contains)
            .ifPresent(dependency -> relations.putValue(moduleDeclaration, dependency));
        }
      }

      if (!relations.isEmpty()) {
        Graph<PsiJavaModule> graph = new SourceSemiGraph(relations);
        DFSTBuilder<PsiJavaModule> builder = new DFSTBuilder<>(graph);
        Collection<Collection<PsiJavaModule>> components = builder.getComponents();
        if (!components.isEmpty()) {
          return components.stream().map(ContainerUtil::newLinkedHashSet).collect(Collectors.toList());
        }
      }
    }

    return Collections.emptyList();
  }

  private static class SourceSemiGraph implements Graph<PsiJavaModule> {
    private final MultiMap<PsiJavaModule, PsiJavaModule> myMap;

    public SourceSemiGraph(MultiMap<PsiJavaModule, PsiJavaModule> map) {
      myMap = map;
    }

    @Override
    public Collection<PsiJavaModule> getNodes() {
      return myMap.keySet();
    }

    @Override
    public Iterator<PsiJavaModule> getIn(PsiJavaModule n) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<PsiJavaModule> getOut(PsiJavaModule n) {
      return myMap.get(n).iterator();
    }
  }
}