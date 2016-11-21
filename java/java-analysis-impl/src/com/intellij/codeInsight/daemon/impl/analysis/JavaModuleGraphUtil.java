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
import com.intellij.psi.impl.source.PsiJavaModuleReference;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.graph.OutboundSemiGraph;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.psi.PsiJavaModule.MODULE_INFO_FILE;
import static com.intellij.psi.util.PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT;

public class JavaModuleGraphUtil {
  private JavaModuleGraphUtil() { }

  @Nullable
  public static PsiJavaModule findDescriptorByElement(@NotNull PsiElement element) {
    PsiFileSystemItem fsItem = element instanceof PsiFileSystemItem ? (PsiFileSystemItem)element : element.getContainingFile();
    return fsItem != null ? ModuleHighlightUtil.getModuleDescriptor(fsItem) : null;
  }

  @Nullable
  public static Collection<PsiJavaModule> findCycle(@NotNull PsiJavaModule module) {
    Project project = module.getProject();
    List<Set<PsiJavaModule>> cycles = CachedValuesManager.getManager(project).getCachedValue(project, () ->
      Result.create(findCycles(project), OUT_OF_CODE_BLOCK_MODIFICATION_COUNT));
    return ContainerUtil.find(cycles, set -> set.contains(module));
  }

  public static boolean exports(@NotNull PsiJavaModule source, @NotNull String packageName, @NotNull PsiJavaModule target) {
    Map<String, Set<String>> exports = CachedValuesManager.getCachedValue(source, () ->
      Result.create(exportsMap(source), source.getContainingFile()));
    Set<String> targets = exports.get(packageName);
    return targets != null && (targets.isEmpty() || targets.contains(target.getModuleName()));
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
        for (PsiRequiresStatement statement : module.getRequires()) {
          PsiJavaModule dependency = PsiJavaModuleReference.resolve(statement, statement.getModuleName(), true);
          if (dependency != null && projectModules.contains(dependency)) {
            relations.putValue(module, dependency);
          }
        }
      }

      if (!relations.isEmpty()) {
        Graph<PsiJavaModule> graph = new ChameleonGraph<>(relations, false);
        DFSTBuilder<PsiJavaModule> builder = new DFSTBuilder<>(graph);
        Collection<Collection<PsiJavaModule>> components = builder.getComponents();
        if (!components.isEmpty()) {
          return components.stream().map(ContainerUtil::newLinkedHashSet).collect(Collectors.toList());
        }
      }
    }

    return Collections.emptyList();
  }

  private static Map<String, Set<String>> exportsMap(@NotNull PsiJavaModule source) {
    Map<String, Set<String>> map = ContainerUtil.newHashMap();
    for (PsiExportsStatement statement : source.getExports()) {
      String pkg = statement.getPackageName();
      List<String> targets = statement.getModuleNames();
      map.put(pkg, targets.isEmpty() ? Collections.emptySet() : ContainerUtil.newTroveSet(targets));
    }
    return map;
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

    Graph<PsiJavaModule> graph = GraphGenerator.generate(new ChameleonGraph<>(relations, true));
    return new RequiresGraph(graph, publicEdges);
  }

  private static void visit(PsiJavaModule module, MultiMap<PsiJavaModule, PsiJavaModule> relations, Set<String> publicEdges) {
    if (!relations.containsKey(module)) {
      relations.putValues(module, Collections.emptyList());
      for (PsiRequiresStatement statement : module.getRequires()) {
        PsiJavaModule dependency = PsiJavaModuleReference.resolve(statement, statement.getModuleName(), false);
        if (dependency != null) {
          relations.putValue(module, dependency);
          if (statement.isPublic()) publicEdges.add(RequiresGraph.key(dependency, module));
          visit(dependency, relations, publicEdges);
        }
      }
    }
  }

  private static class RequiresGraph {
    private final OutboundSemiGraph<PsiJavaModule> myGraph;
    private final Set<String> myPublicEdges;

    public RequiresGraph(OutboundSemiGraph<PsiJavaModule> graph, Set<String> publicEdges) {
      myGraph = graph;
      myPublicEdges = publicEdges;
    }

    public boolean reads(PsiJavaModule source, PsiJavaModule destination) {
      Collection<PsiJavaModule> nodes = myGraph.getNodes();
      if (nodes.contains(destination) && nodes.contains(source)) {
        Iterator<PsiJavaModule> directReaders = myGraph.getOut(destination);
        while (directReaders.hasNext()) {
          PsiJavaModule next = directReaders.next();
          if (source.equals(next) || myPublicEdges.contains(key(destination, next)) && reads(source, next)) {
            return true;
          }
        }
      }
      return false;
    }

    public static String key(PsiJavaModule module, PsiJavaModule exporter) {
      return module.getModuleName() + '/' + exporter.getModuleName();
    }
  }

  private static class ChameleonGraph<N> implements Graph<N> {
    private final Set<N> myNodes;
    private final MultiMap<N, N> myEdges;
    private final boolean myInbound;

    public ChameleonGraph(MultiMap<N, N> edges, boolean inbound) {
      myNodes = new THashSet<>();
      edges.entrySet().forEach(e -> {
        myNodes.add(e.getKey());
        myNodes.addAll(e.getValue());
      });
      myEdges = edges;
      myInbound = inbound;
    }

    @Override
    public Collection<N> getNodes() {
      return myNodes;
    }

    @Override
    public Iterator<N> getIn(N n) {
      return myInbound ? myEdges.get(n).iterator() : Collections.emptyIterator();
    }

    @Override
    public Iterator<N> getOut(N n) {
      return myInbound ? Collections.emptyIterator() : myEdges.get(n).iterator();
    }
  }
}