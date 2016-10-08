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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
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
      Result.create(findCycles(project), OUT_OF_CODE_BLOCK_MODIFICATION_COUNT));
    return ContainerUtil.find(cycles, set -> set.contains(module));
  }

  public static boolean reads(@NotNull PsiJavaModule source, @NotNull PsiJavaModule destination) {
    Project project = source.getProject();
    RequiresGraph graph = CachedValuesManager.getManager(project).getCachedValue(project, () ->
      Result.create(buildRequiresGraph(project), OUT_OF_CODE_BLOCK_MODIFICATION_COUNT));
    return graph.reads(source, destination);
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
      for (PsiJavaModule module : projectModules) {
        for (PsiRequiresStatement statement : psiTraverser().children(module).filter(PsiRequiresStatement.class)) {
          Optional.ofNullable(statement.getReferenceElement())
            .map(PsiJavaModuleReferenceElement::getReference)
            .map(ref -> ref.multiResolve(true))
            .map(a -> a.length == 1 ? a[0].getElement() : null)
            .map(e -> e instanceof PsiJavaModule ? (PsiJavaModule)e : null)
            .filter(projectModules::contains)
            .ifPresent(dependency -> relations.putValue(module, dependency));
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

  // Starting from source modules, collects all module dependencies in the project.
  // The resulting graph is used for tracing readability.
  private static RequiresGraph buildRequiresGraph(Project project) {
    MultiMap<PsiJavaModule, PsiJavaModule> relations = MultiMap.create();
    Set<String> publicEdges = ContainerUtil.newTroveSet();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(project, MODULE_INFO_FILE, module.getModuleScope(false));
      Optional.ofNullable(ContainerUtil.getFirstItem(files))
        .map(PsiManager.getInstance(project)::findFile)
        .map(f -> f instanceof PsiJavaFile ? ((PsiJavaFile)f).getModuleDeclaration() : null)
        .ifPresent(m -> visit(m, relations, publicEdges));
    }

    GraphGenerator<PsiJavaModule> graph = GraphGenerator.create(new RequiresSemiGraph(relations));
    return new RequiresGraph(graph, publicEdges);
  }

  private static void visit(PsiJavaModule module, MultiMap<PsiJavaModule, PsiJavaModule> relations, Set<String> publicEdges) {
    if (!relations.containsKey(module)) {
      relations.putValues(module, Collections.emptyList());
      for (PsiRequiresStatement statement : psiTraverser().children(module).filter(PsiRequiresStatement.class)) {
        Optional.ofNullable(statement.getReferenceElement())
          .map(PsiJavaModuleReferenceElement::getReference)
          .map(PsiReference::resolve)
          .map(e -> e instanceof PsiJavaModule ? (PsiJavaModule)e : null)
          .ifPresent(dependency -> {
            relations.putValue(module, dependency);
            if (isPublic(statement)) publicEdges.add(RequiresGraph.key(dependency, module));
            visit(dependency, relations, publicEdges);
          });
      }
    }
  }

  private static boolean isPublic(PsiRequiresStatement statement) {
    for (PsiElement child = statement.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (PsiUtil.isJavaToken(child, JavaTokenType.PUBLIC_KEYWORD)) return true;
      if (child instanceof PsiJavaModuleReferenceElement) break;
    }
    return false;
  }

  private static class RequiresGraph {
    private final Graph<PsiJavaModule> myGraph;
    private final Set<String> myPublicEdges;

    public RequiresGraph(Graph<PsiJavaModule> graph, Set<String> publicEdges) {
      myGraph = graph;
      myPublicEdges = publicEdges;
    }

    public boolean reads(PsiJavaModule source, PsiJavaModule destination) {
      Iterator<PsiJavaModule> directReaders = myGraph.getOut(destination);
      while (directReaders.hasNext()) {
        PsiJavaModule next = directReaders.next();
        if (source.equals(next) || myPublicEdges.contains(key(destination, next)) && reads(source, next)) {
          return true;
        }
      }
      return false;
    }

    public static String key(PsiJavaModule module, PsiJavaModule exporter) {
      return module.getModuleName() + '/' + exporter.getModuleName();
    }
  }

  //<editor-fold desc="Helpers.">
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

  private static class RequiresSemiGraph implements GraphGenerator.SemiGraph<PsiJavaModule> {
    private final MultiMap<PsiJavaModule, PsiJavaModule> myMap;

    public RequiresSemiGraph(MultiMap<PsiJavaModule, PsiJavaModule> map) {
      myMap = map;
    }

    @Override
    public Collection<PsiJavaModule> getNodes() {
      return myMap.keySet();
    }

    @Override
    public Iterator<PsiJavaModule> getIn(PsiJavaModule n) {
      return myMap.get(n).iterator();
    }
  }
  //</editor-fold>
}