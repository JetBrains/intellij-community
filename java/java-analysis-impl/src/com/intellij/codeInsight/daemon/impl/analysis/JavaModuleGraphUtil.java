// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.lang.jvm.JvmLanguage;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.*;
import java.util.function.BiFunction;
import java.util.jar.JarFile;

public final class JavaModuleGraphUtil {
  private JavaModuleGraphUtil() { }

  public static @Nullable PsiJavaModule findDescriptorByElement(@Nullable PsiElement element) {
    if (element != null) {
      PsiFileSystemItem fsItem = element instanceof PsiFileSystemItem ? (PsiFileSystemItem)element : element.getContainingFile();
      if (fsItem != null) {
        return findDescriptorByFile(fsItem.getVirtualFile(), fsItem.getProject());
      }
    }

    return null;
  }

  public static @Nullable PsiJavaModule findDescriptorByFile(@Nullable VirtualFile file, @NotNull Project project) {
    if (file != null) {
      ProjectFileIndex index = ProjectFileIndex.getInstance(project);
      if (index.isInLibrary(file)) {
        return findDescriptorInLibrary(project, index, file);
      }

      Module module = index.getModuleForFile(file);
      if (module != null) {
        return findDescriptorByModule(module, index.isInTestSourceContent(file));
      }
    }

    return null;
  }

  private static PsiJavaModule findDescriptorInLibrary(Project project, ProjectFileIndex index, VirtualFile file) {
    VirtualFile root = index.getClassRootForFile(file);
    if (root != null) {
      VirtualFile descriptorFile = JavaModuleNameIndex.descriptorFile(root);
      if (descriptorFile != null) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(descriptorFile);
        if (psiFile instanceof PsiJavaFile) {
          return ((PsiJavaFile)psiFile).getModuleDeclaration();
        }
      }
      else if (root.getFileSystem() instanceof JarFileSystem && "jar".equalsIgnoreCase(root.getExtension())) {
        PsiDirectory rootPsi = PsiManager.getInstance(project).findDirectory(root);
        assert rootPsi != null : root;
        return CachedValuesManager.getCachedValue(rootPsi, () -> {
          VirtualFile _root = rootPsi.getVirtualFile();
          LightJavaModule result = LightJavaModule.create(rootPsi.getManager(), _root, LightJavaModule.moduleName(_root));
          return Result.create(result, _root, ProjectRootModificationTracker.getInstance(rootPsi.getProject()));
        });
      }
    }
    else {
      root = index.getSourceRootForFile(file);
      if (root != null) {
        VirtualFile moduleDescriptor = root.findChild(PsiJavaModule.MODULE_INFO_FILE);
        PsiFile psiFile = moduleDescriptor != null ? PsiManager.getInstance(project).findFile(moduleDescriptor) : null;
        if (psiFile instanceof PsiJavaFile) {
          return ((PsiJavaFile)psiFile).getModuleDeclaration();
        }
      }
    }

    return null;
  }

  public static @Nullable PsiJavaModule findDescriptorByModule(@Nullable Module module, boolean inTests) {
    if (module == null) return null;
    CachedValuesManager valuesManager = CachedValuesManager.getManager(module.getProject());
    PsiJavaModule javaModule = inTests //to have different providers for production and tests
                            ? valuesManager.getCachedValue(module, () -> createModuleCacheResult(module, true))
                            : valuesManager.getCachedValue(module, () -> createModuleCacheResult(module, false));
    return javaModule != null && javaModule.isValid() ? javaModule : null;
  }

  @NotNull
  private static Result<PsiJavaModule> createModuleCacheResult(@NotNull Module module,
                                                               boolean inTests) {
    Project project = module.getProject();
    return new Result<>(findDescriptionByModuleInner(module, inTests), 
                        ProjectRootModificationTracker.getInstance(project), 
                        PsiModificationTracker.SERVICE.getInstance(project));
  }

  @Nullable
  private static PsiJavaModule findDescriptionByModuleInner(@NotNull Module module, boolean inTests) {
    Project project = module.getProject();
    GlobalSearchScope moduleScope = module.getModuleScope();
    if (!DumbService.isDumb(project) &&
        FilenameIndex.getVirtualFilesByName(PsiJavaModule.MODULE_INFO_FILE, moduleScope).isEmpty() &&
        FilenameIndex.getVirtualFilesByName("MANIFEST.MF", moduleScope).isEmpty()) {
      return null;
    }
    JavaSourceRootType rootType = inTests ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    List<VirtualFile> sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(rootType);
    List<VirtualFile> files = ContainerUtil.mapNotNull(sourceRoots, root -> root.findChild(PsiJavaModule.MODULE_INFO_FILE));
    if (files.size() == 1) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(files.get(0));
      if (psiFile instanceof PsiJavaFile) {
        return ((PsiJavaFile)psiFile).getModuleDeclaration();
      }
    }
    else if (files.isEmpty()) {
      files = ContainerUtil.mapNotNull(sourceRoots, root -> root.findFileByRelativePath(JarFile.MANIFEST_NAME));
      if (files.size() == 1) {
        VirtualFile manifest = files.get(0);
        PsiFile manifestPsi = PsiManager.getInstance(project).findFile(manifest);
        assert manifestPsi != null : manifest;
        return CachedValuesManager.getCachedValue(manifestPsi, () -> {
          String name = LightJavaModule.claimedModuleName(manifest);
          LightJavaModule result =
            name != null ? LightJavaModule.create(PsiManager.getInstance(project), manifest.getParent().getParent(), name) : null;
          return Result.create(result, manifestPsi, ProjectRootModificationTracker.getInstance(project));
        });
      }
    }

    return null;
  }

  public static @NotNull Collection<PsiJavaModule> findCycle(@NotNull PsiJavaModule module) {
    Project project = module.getProject();
    List<Set<PsiJavaModule>> cycles = CachedValuesManager.getManager(project).getCachedValue(project, () ->
      Result.create(findCycles(project), PsiModificationTracker.MODIFICATION_COUNT));
    return Objects.requireNonNullElse(ContainerUtil.find(cycles, set -> set.contains(module)), Collections.emptyList());
  }

  public static boolean exports(@NotNull PsiJavaModule source, @NotNull String packageName, @Nullable PsiJavaModule target) {
    Map<String, Set<String>> exports = CachedValuesManager.getCachedValue(source, () ->
      Result.create(exportsMap(source), source.getContainingFile()));
    Set<String> targets = exports.get(packageName);
    return targets != null && (targets.isEmpty() || target != null && targets.contains(target.getName()));
  }

  public static boolean reads(@NotNull PsiJavaModule source, @NotNull PsiJavaModule destination) {
    return getRequiresGraph(source).reads(source, destination);
  }

  public static @NotNull Set<PsiJavaModule> getAllDependencies(PsiJavaModule source) {
    return getRequiresGraph(source).getAllDependencies(source);
  }

  public static @Nullable Trinity<String, PsiJavaModule, PsiJavaModule> findConflict(@NotNull PsiJavaModule module) {
    return getRequiresGraph(module).findConflict(module);
  }

  public static @Nullable PsiJavaModule findOrigin(@NotNull PsiJavaModule module, @NotNull String packageName) {
    return getRequiresGraph(module).findOrigin(module, packageName);
  }

  /*
   * Looks for cycles between Java modules in the project sources.
   * Library/JDK modules are excluded in an assumption there can't be any lib -> src dependencies.
   * Module references are resolved "globally" (i.e., without taking project dependencies into account).
   */
  private static @NotNull List<Set<PsiJavaModule>> findCycles(Project project) {
    Set<PsiJavaModule> projectModules = new HashSet<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      List<PsiJavaModule> descriptors = ContainerUtil.mapNotNull(moduleRootManager.getSourceRoots(true),
        root -> findDescriptorByFile(root, project));
      if (descriptors.size() > 2) return Collections.emptyList();  // aborts the process when there are incorrect modules in the project

      if (descriptors.size() == 2) {
        if (descriptors.stream()
              .map(d -> PsiUtilCore.getVirtualFile(d))
              .filter(Objects::nonNull)
              .map(moduleRootManager.getFileIndex()::isInTestSourceContent).count() < 2) {
          return Collections.emptyList();
        }
        projectModules.addAll(descriptors);
      }
      if (descriptors.size() == 1) projectModules.add(descriptors.get(0));
    }

    if (!projectModules.isEmpty()) {
      MultiMap<PsiJavaModule, PsiJavaModule> relations = MultiMap.create();
      for (PsiJavaModule module : projectModules) {
        for (PsiRequiresStatement statement : module.getRequires()) {
          PsiJavaModuleReference ref = statement.getModuleReference();
          if (ref != null) {
            ResolveResult[] results = ref.multiResolve(true);
            if (results.length == 1) {
              PsiJavaModule dependency = (PsiJavaModule)results[0].getElement();
              if (dependency != null && projectModules.contains(dependency)) {
                relations.putValue(module, dependency);
              }
            }
          }
        }
      }

      if (!relations.isEmpty()) {
        Graph<PsiJavaModule> graph = new ChameleonGraph<>(relations, false);
        DFSTBuilder<PsiJavaModule> builder = new DFSTBuilder<>(graph);
        Collection<Collection<PsiJavaModule>> components = builder.getComponents();
        if (!components.isEmpty()) {
          return ContainerUtil.map(components, elements -> new LinkedHashSet<>(elements));
        }
      }
    }

    return Collections.emptyList();
  }

  private static Map<String, Set<String>> exportsMap(@NotNull PsiJavaModule source) {
    Map<String, Set<String>> map = new HashMap<>();
    for (PsiPackageAccessibilityStatement statement : source.getExports()) {
      String pkg = statement.getPackageName();
      List<String> targets = statement.getModuleNames();
      map.put(pkg, targets.isEmpty() ? Collections.emptySet() : new HashSet<>(targets));
    }
    return map;
  }

  private static RequiresGraph getRequiresGraph(PsiJavaModule module) {
    Project project = module.getProject();
    return CachedValuesManager.getManager(project).getCachedValue(project, () ->
      Result.create(buildRequiresGraph(project), PsiModificationTracker.MODIFICATION_COUNT));
  }

  /*
   * Collects all module dependencies in the project.
   * The resulting graph is used for tracing readability and checking package conflicts.
   */
  private static RequiresGraph buildRequiresGraph(Project project) {
    MultiMap<PsiJavaModule, PsiJavaModule> relations = MultiMap.create();
    Set<String> transitiveEdges = new HashSet<>();

    JavaModuleNameIndex index = JavaModuleNameIndex.getInstance();
    GlobalSearchScope scope = ProjectScope.getAllScope(project);
    for (String key : index.getAllKeys(project)) {
      for (PsiJavaModule module : index.get(key, project, scope)) {
        visit(module, relations, transitiveEdges);
      }
    }

    Graph<PsiJavaModule> graph = GraphGenerator.generate(new ChameleonGraph<>(relations, true));
    return new RequiresGraph(graph, transitiveEdges);
  }

  private static void visit(PsiJavaModule module, MultiMap<PsiJavaModule, PsiJavaModule> relations, Set<String> transitiveEdges) {
    if (!(module instanceof LightJavaModule) && !relations.containsKey(module)) {
      relations.putValues(module, Collections.emptyList());
      boolean explicitJavaBase = false;
      for (PsiRequiresStatement statement : module.getRequires()) {
        PsiJavaModuleReference ref = statement.getModuleReference();
        if (ref != null) {
          if (PsiJavaModule.JAVA_BASE.equals(ref.getCanonicalText())) explicitJavaBase = true;
          for (ResolveResult result : ref.multiResolve(false)) {
            PsiJavaModule dependency = (PsiJavaModule)result.getElement();
            assert dependency != null : result;
            relations.putValue(module, dependency);
            if (statement.hasModifierProperty(PsiModifier.TRANSITIVE)) transitiveEdges.add(RequiresGraph.key(dependency, module));
            visit(dependency, relations, transitiveEdges);
          }
        }
      }
      if (!explicitJavaBase) {
        PsiJavaModule javaBase = JavaPsiFacade.getInstance(module.getProject()).findModule(PsiJavaModule.JAVA_BASE, module.getResolveScope());
        if (javaBase != null) relations.putValue(module, javaBase);
      }
    }
  }

  private static final class RequiresGraph {
    private final Graph<PsiJavaModule> myGraph;
    private final Set<String> myTransitiveEdges;

    private RequiresGraph(Graph<PsiJavaModule> graph, Set<String> transitiveEdges) {
      myGraph = graph;
      myTransitiveEdges = transitiveEdges;
    }

    public boolean reads(PsiJavaModule source, PsiJavaModule destination) {
      Collection<PsiJavaModule> nodes = myGraph.getNodes();
      if (nodes.contains(destination) && nodes.contains(source)) {
        Iterator<PsiJavaModule> directReaders = myGraph.getOut(destination);
        while (directReaders.hasNext()) {
          PsiJavaModule next = directReaders.next();
          if (source.equals(next) || myTransitiveEdges.contains(key(destination, next)) && reads(source, next)) {
            return true;
          }
        }
      }
      return false;
    }

    public @Nullable Trinity<String, PsiJavaModule, PsiJavaModule> findConflict(PsiJavaModule source) {
      Map<String, PsiJavaModule> exports = new HashMap<>();
      return processExports(source, (pkg, m) -> {
        PsiJavaModule found = exports.put(pkg, m);
        return found == null ||
               found instanceof LightJavaModule && m instanceof LightJavaModule ||
               found.getName().equals(m.getName())
               ? null : new Trinity<>(pkg, found, m);
      });
    }

    public @Nullable PsiJavaModule findOrigin(PsiJavaModule module, String packageName) {
      return processExports(module, (pkg, m) -> packageName.equals(pkg) ? m : null);
    }

    private <T> @Nullable T processExports(PsiJavaModule start, BiFunction<? super String, ? super PsiJavaModule, ? extends T> processor) {
      return myGraph.getNodes().contains(start) ? processExports(start.getName(), start, true, new HashSet<>(), processor) : null;
    }

    private <T> @Nullable T processExports(String name,
                                           PsiJavaModule module,
                                           boolean direct,
                                           Set<? super PsiJavaModule> visited,
                                           BiFunction<? super String, ? super PsiJavaModule, ? extends T> processor) {
      if (visited.add(module)) {
        if (!direct) {
          for (PsiPackageAccessibilityStatement statement : module.getExports()) {
            List<String> exportTargets = statement.getModuleNames();
            if (exportTargets.isEmpty() || exportTargets.contains(name)) {
              T result = processor.apply(statement.getPackageName(), module);
              if (result != null) return result;
            }
          }
        }
        for (Iterator<PsiJavaModule> iterator = myGraph.getIn(module); iterator.hasNext();) {
          PsiJavaModule dependency = iterator.next();
          if (direct || myTransitiveEdges.contains(key(dependency, module))) {
            T result = processExports(name, dependency, false, visited, processor);
            if (result != null) return result;
          }
        }
      }

      return null;
    }

    public static String key(PsiJavaModule module, PsiJavaModule exporter) {
      return module.getName() + '/' + exporter.getName();
    }

    public Set<PsiJavaModule> getAllDependencies(PsiJavaModule module) {
      Set<PsiJavaModule> requires = new HashSet<>();
      collectDependencies(module, requires);
      return requires;
    }

    private void collectDependencies(PsiJavaModule module, Set<PsiJavaModule> dependencies) {
      for (Iterator<PsiJavaModule> iterator = myGraph.getIn(module); iterator.hasNext();) {
        PsiJavaModule dependency = iterator.next();
        if (!dependencies.contains(dependency)) {
          dependencies.add(dependency);
          collectDependencies(dependency, dependencies);
        }
      }
    }
  }

  private static final class ChameleonGraph<N> implements Graph<N> {
    private final Set<N> myNodes;
    private final MultiMap<N, N> myEdges;
    private final boolean myInbound;

    private ChameleonGraph(MultiMap<N, N> edges, boolean inbound) {
      myNodes = new HashSet<>();
      edges.entrySet().forEach(e -> {
        myNodes.add(e.getKey());
        myNodes.addAll(e.getValue());
      });
      myEdges = edges;
      myInbound = inbound;
    }

    @Override
    public @NotNull Collection<N> getNodes() {
      return myNodes;
    }

    @Override
    public @NotNull Iterator<N> getIn(N n) {
      return myInbound ? myEdges.get(n).iterator() : Collections.emptyIterator();
    }

    @Override
    public @NotNull Iterator<N> getOut(N n) {
      return myInbound ? Collections.emptyIterator() : myEdges.get(n).iterator();
    }
  }

  public static class JavaModuleScope extends GlobalSearchScope {
    private final PsiJavaModule myModule;
    private final boolean myIncludeLibraries;
    private final boolean myIsInTests;

    private JavaModuleScope(@NotNull Project project, @NotNull PsiJavaModule module, @NotNull VirtualFile moduleFile) {
      super(project);
      myModule = module;
      ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(project);
      myIncludeLibraries = fileIndex.isInLibrary(moduleFile);
      myIsInTests = !myIncludeLibraries && fileIndex.isInTestSourceContent(moduleFile);
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return findDescriptorByModule(aModule, myIsInTests) == myModule;
    }

    @Override
    public boolean isSearchInLibraries() {
      return myIncludeLibraries;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      Project project = getProject();
      if (project == null) return false;
      if (!isJvmLanguageFile(file)) return false;
      ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(project);
      if (index.isInLibrary(file)) return myIncludeLibraries && myModule.equals(findDescriptorInLibrary(project, index, file));
      Module module = index.getModuleForFile(file);
      return myModule.equals(findDescriptorByModule(module, myIsInTests));
    }

    private static boolean isJvmLanguageFile(@NotNull VirtualFile file) {
      FileTypeRegistry fileTypeRegistry = FileTypeRegistry.getInstance();
      LanguageFileType languageFileType = ObjectUtils.tryCast(fileTypeRegistry.getFileTypeByFileName(file.getName()), LanguageFileType.class);
      return languageFileType != null && languageFileType.getLanguage() instanceof JvmLanguage;
    }

    public static @Nullable JavaModuleScope moduleScope(@NotNull PsiJavaModule module) {
      PsiFile moduleFile = module.getContainingFile();
      if (moduleFile == null) return null;
      VirtualFile virtualFile = moduleFile.getVirtualFile();
      if (virtualFile == null) return null;
      return new JavaModuleScope(module.getProject(), module, virtualFile);
    }
  }
}
