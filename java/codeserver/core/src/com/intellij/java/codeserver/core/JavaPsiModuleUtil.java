// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiJavaModuleModificationTracker;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.JavaModuleSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.*;
import java.util.function.BiFunction;
import java.util.jar.JarFile;

import static com.intellij.psi.PsiJavaModule.JAVA_BASE;
import static java.util.Objects.requireNonNullElse;

/**
 * Utilities related to JPMS modules
 */
public final class JavaPsiModuleUtil {
  /**
   * @param element PSI element that belongs to the module
   * @return JPMS module the supplied element belongs to; null if no module definition is found
   */
  @Contract("null->null")
  public static @Nullable PsiJavaModule findDescriptorByElement(@Nullable PsiElement element) {
    if (element == null) return null;
    if (element instanceof PsiJavaModule module) return module;
    if (element.getContainingFile() instanceof PsiJavaFile file) {
      PsiJavaModule module = file.getModuleDeclaration();
      if (module != null) return module;
    }

    if (element instanceof PsiFileSystemItem fsItem) {
      return findDescriptorByFile(fsItem.getVirtualFile(), fsItem.getProject());
    }

    PsiFile file = element.getContainingFile();
    if (file != null) {
      return findDescriptorByFile(file.getVirtualFile(), file.getProject());
    }

    if (element instanceof PsiPackage psiPackage) {
      PsiDirectory[] directories = psiPackage.getDirectories(ProjectScope.getLibrariesScope(psiPackage.getProject()));
      for (PsiDirectory directory : directories) {
        PsiJavaModule descriptor = findDescriptorByFile(directory.getVirtualFile(), directory.getProject());
        if (descriptor != null) return descriptor;
      }
    }
    return null;
  }

  /**
   * @param file virtual file that belongs to a module
   * @param project current project
   * @return JPMS module the supplied file belongs to; null if no module definition is found
   */
  @Contract("null, _->null")
  public static @Nullable PsiJavaModule findDescriptorByFile(@Nullable VirtualFile file, @NotNull Project project) {
    if (file == null) return null;
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    return index.isInLibrary(file)
           ? findDescriptorInLibrary(file, project)
           : findDescriptorByModule(index.getModuleForFile(file), index.isInTestSourceContent(file));
  }

  /**
   * @param library library that declares a JPMS module
   * @param project current project
   * @return JPMS module declared by the supplied library; null if not found
   */
  @Contract("null, _ -> null")
  public static @Nullable PsiJavaModule findDescriptorByLibrary(@Nullable Library library, @NotNull Project project) {
    if (library == null) return null;
    final VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
    if (files.length == 0) return null;
    final PsiJavaModule javaModule = findDescriptorInLibrary(files[0], project);
    return javaModule != null && javaModule.isValid() ? javaModule : null;
  }

  /**
   * @param file library content root
   * @param project current project
   * @return JPMS module declared by the supplied library; null if not found
   */
  public static @Nullable PsiJavaModule findDescriptorInLibrary(@NotNull VirtualFile file, @NotNull Project project) {
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    VirtualFile root = index.getClassRootForFile(file);
    if (root != null) {
      VirtualFile descriptorFile =
        JavaMultiReleaseUtil.findVersionSpecificFile(root, PsiJavaModule.MODULE_INFO_CLS_FILE, LanguageLevel.HIGHEST);
      if (descriptorFile != null) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(descriptorFile);
        if (psiFile instanceof PsiJavaFile) {
          return ((PsiJavaFile)psiFile).getModuleDeclaration();
        }
      }
      else if (root.getFileType() instanceof ArchiveFileType && "jar".equalsIgnoreCase(root.getExtension())) {
        PsiDirectory rootPsi = PsiManager.getInstance(project).findDirectory(root);
        assert rootPsi != null : root;
        return CachedValuesManager.getCachedValue(rootPsi, () -> {
          VirtualFile _root = rootPsi.getVirtualFile();
          LightJavaModule result = LightJavaModule.create(rootPsi.getManager(), _root, LightJavaModule.moduleName(_root));
          return CachedValueProvider.Result.create(result, _root, ProjectRootModificationTracker.getInstance(rootPsi.getProject()));
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

  /**
   * @param module IntelliJ IDEA module
   * @param inTests if true, the search will be performed in test root
   * @return JPMS module that corresponds to a given IntelliJ IDEA module; null if not found
   */
  @Contract("null,_->null")
  public static @Nullable PsiJavaModule findDescriptorByModule(@Nullable Module module, boolean inTests) {
    if (module == null) return null;
    CachedValuesManager valuesManager = CachedValuesManager.getManager(module.getProject());
    PsiJavaModule javaModule = inTests //to have different providers for production and tests
                               ? valuesManager.getCachedValue(module, () -> createModuleCacheResult(module, true))
                               : valuesManager.getCachedValue(module, () -> createModuleCacheResult(module, false));
    return javaModule != null && javaModule.isValid() ? javaModule : null;
  }

  private static @NotNull CachedValueProvider.Result<PsiJavaModule> createModuleCacheResult(@NotNull Module module,
                                                                                            boolean inTests) {
    Project project = module.getProject();
    return new CachedValueProvider.Result<>(findDescriptionByModuleInner(module, inTests),
                                            ProjectRootModificationTracker.getInstance(project),
                                            PsiJavaModuleModificationTracker.getInstance(project));
  }

  private static @Nullable PsiJavaModule findDescriptionByModuleInner(@NotNull Module module, boolean inTests) {
    Project project = module.getProject();
    GlobalSearchScope moduleScope = module.getModuleScope();
    String virtualAutoModuleName = JavaManifestUtil.getManifestAttributeValue(module, PsiJavaModule.AUTO_MODULE_NAME);
    if (!DumbService.isDumb(project) &&
        FilenameIndex.getVirtualFilesByName(PsiJavaModule.MODULE_INFO_FILE, moduleScope).isEmpty() &&
        FilenameIndex.getVirtualFilesByName("MANIFEST.MF", moduleScope).isEmpty() &&
        virtualAutoModuleName == null) {
      return null;
    }
    JavaSourceRootType rootType = inTests ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    Set<VirtualFile> excludeRoots = ContainerUtil.newHashSet(ModuleRootManager.getInstance(module).getExcludeRoots());
    List<VirtualFile> sourceRoots = ContainerUtil.filter(rootManager.getSourceRoots(rootType), root -> !excludeRoots.contains(root));

    List<VirtualFile> files = ContainerUtil.mapNotNull(sourceRoots, root -> root.findChild(PsiJavaModule.MODULE_INFO_FILE));
    if (files.isEmpty()) {
      JavaResourceRootType resourceRootType = inTests ? JavaResourceRootType.TEST_RESOURCE : JavaResourceRootType.RESOURCE;
      List<VirtualFile> roots = new ArrayList<>(rootManager.getSourceRoots(resourceRootType));
      roots.addAll(sourceRoots);
      files = ContainerUtil.mapNotNull(roots, root -> root.findFileByRelativePath(JarFile.MANIFEST_NAME));
      if (files.size() == 1 || new HashSet<>(files).size() == 1) {
        VirtualFile manifest = files.get(0);
        PsiFile manifestPsi = PsiManager.getInstance(project).findFile(manifest);
        assert manifestPsi != null : manifest;
        return CachedValuesManager.getCachedValue(manifestPsi, () -> {
          String name = LightJavaModule.claimedModuleName(manifest);
          LightJavaModule result =
            name != null ? LightJavaModule.create(PsiManager.getInstance(project), manifest.getParent().getParent(), name) : null;
          return CachedValueProvider.Result.create(result, manifestPsi, ProjectRootModificationTracker.getInstance(project));
        });
      }
      List<VirtualFile> sourceSourceRoots = rootManager.getSourceRoots(JavaSourceRootType.SOURCE);
      if (virtualAutoModuleName != null && !sourceSourceRoots.isEmpty()) {
        return LightJavaModule.create(PsiManager.getInstance(project), sourceSourceRoots.get(0), virtualAutoModuleName);
      }
    }
    else {
      final VirtualFile file = files.get(0);
      if (ContainerUtil.and(files, f -> f.equals(file))) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile instanceof PsiJavaFile) {
          return ((PsiJavaFile)psiFile).getModuleDeclaration();
        }
      }
    }

    return null;
  }

  /**
   * @param source source module
   * @param destination target module
   * @return true if the source module reads the target module
   */
  public static boolean reads(@NotNull PsiJavaModule source, @NotNull PsiJavaModule destination) {
    return getRequiresGraph(source).reads(source, destination);
  }

  /**
   * @param source source module
   * @return set of all direct (non-transitive) dependencies of the source module
   */
  public static @NotNull Set<PsiJavaModule> getAllDependencies(@NotNull PsiJavaModule source) {
    return getRequiresGraph(source).getAllDependencies(source, false);
  }

  /**
   * @param source source module
   * @return set of all direct and indirect (transitive) dependencies of the source module
   */
  public static @NotNull Set<PsiJavaModule> getAllTransitiveDependencies(@NotNull PsiJavaModule source) {
    return getRequiresGraph(source).getAllDependencies(source, true);
  }

  /**
   * @param module module to check for dependency cycles
   * @return collection of modules that form a dependency cycle
   */
  public static @NotNull Collection<PsiJavaModule> findCycle(@NotNull PsiJavaModule module) {
    Project project = module.getProject();
    List<Set<PsiJavaModule>> cycles = CachedValuesManager.getManager(project).getCachedValue(project, () ->
      CachedValueProvider.Result.create(findCycles(project),
                                        PsiJavaModuleModificationTracker.getInstance(project),
                                        ProjectRootModificationTracker.getInstance(project)));
    return requireNonNullElse(ContainerUtil.find(cycles, set -> set.contains(module)), Collections.emptyList());
  }

  private static @Nullable VirtualFile getVirtualFile(@NotNull PsiJavaModule module) {
    if (module instanceof LightJavaModule light) {
      return light.getRootVirtualFile();
    }
    return PsiUtilCore.getVirtualFile(module);
  }

  /*
   * Looks for cycles between Java modules in the project sources.
   * Library/JDK modules are excluded in an assumption there can't be any lib -> src dependencies.
   * Module references are resolved "globally" (i.e., without taking project dependencies into account).
   */
  private static @Unmodifiable @NotNull List<Set<PsiJavaModule>> findCycles(@NotNull Project project) {
    Set<PsiJavaModule> projectModules = new HashSet<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      List<PsiJavaModule> descriptors = ContainerUtil.mapNotNull(moduleRootManager.getSourceRoots(true),
                                                                 root -> findDescriptorByFile(root, project));
      if (descriptors.size() > 2) return Collections.emptyList();  // aborts the process when there are incorrect modules in the project

      if (descriptors.size() == 2) {
        if (descriptors.stream()
              .map(d -> getVirtualFile(d))
              .filter(Objects::nonNull).count() < 2) {
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

  /**
   * @param source source module
   * @param packageName package name in source module
   * @param target target module
   * @return true if a source module exports a specified package to the target module, or to everybody (if the target module is null)
   */
  public static boolean exports(@NotNull PsiJavaModule source, @NotNull String packageName, @Nullable PsiJavaModule target) {
    Map<String, Set<String>> exports = CachedValuesManager.getCachedValue(source, () ->
      CachedValueProvider.Result.create(exportsMap(source), source.getContainingFile()));
    Set<String> targets = exports.get(packageName);
    return targets != null && (targets.isEmpty() || target != null && targets.contains(target.getName()));
  }

  private static @NotNull Map<String, Set<String>> exportsMap(@NotNull PsiJavaModule source) {
    Map<String, Set<String>> map = new HashMap<>();
    for (PsiPackageAccessibilityStatement statement : source.getExports()) {
      String pkg = statement.getPackageName();
      List<String> targets = statement.getModuleNames();
      map.put(pkg, targets.isEmpty() ? Collections.emptySet() : new HashSet<>(targets));
    }
    return map;
  }

  /**
   * Represents a dependency conflict when a single package is imported from two modules
   * @param packageName package name
   * @param module1 first module from which this package is being read
   * @param module2 second module from which this package is being read
   */
  public record ModulePackageConflict(@NotNull String packageName, @NotNull PsiJavaModule module1, @NotNull PsiJavaModule module2) { }

  /**
   * @param module module to check 
   * @return a {@link ModulePackageConflict} representing a package name conflict among module dependencies; null if no conflict found.
   * If there are several conflicts, only the first one is returned.
   */
  public static @Nullable ModulePackageConflict findConflict(@NotNull PsiJavaModule module) {
    return getRequiresGraph(module).findConflict(module);
  }

  /**
   * @param module module that accesses a package
   * @param packageName the name of the accessed package
   * @return the module where the package is declared; null if not found
   */
  public static @Nullable PsiJavaModule findOrigin(@NotNull PsiJavaModule module, @NotNull String packageName) {
    return getRequiresGraph(module).findOrigin(module, packageName);
  }

  private static RequiresGraph getRequiresGraph(@NotNull PsiJavaModule module) {
    final Project project = module.getProject();
    if (DumbService.getInstance(project).isAlternativeResolveEnabled()) {
      return DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> buildRequiresGraph(project));
    }
    return CachedValuesManager.getManager(project).getCachedValue(project, () ->
      CachedValueProvider.Result.create(buildRequiresGraph(project),
                                        PsiJavaModuleModificationTracker.getInstance(project),
                                        ProjectRootModificationTracker.getInstance(project)));
  }

  /*
   * Collects all module dependencies in the project.
   * The resulting graph is used for tracing readability and checking package conflicts.
   */
  private static @NotNull RequiresGraph buildRequiresGraph(@NotNull Project project) {
    MultiMap<PsiJavaModule, PsiJavaModule> relations = MultiMap.create();
    Set<String> transitiveEdges = new HashSet<>();

    GlobalSearchScope scope = ProjectScope.getAllScope(project);
    JavaModuleSearch.allModules(project, scope).forEach(module -> {
      visit(module, relations, transitiveEdges);
      return true;
    });

    Graph<PsiJavaModule> graph = GraphGenerator.generate(new ChameleonGraph<>(relations, true));
    return new RequiresGraph(graph, transitiveEdges);
  }

  private static void visit(@NotNull PsiJavaModule module, @NotNull MultiMap<PsiJavaModule, PsiJavaModule> relations, @NotNull Set<String> transitiveEdges) {
    if (!(module instanceof LightJavaModule) && !relations.containsKey(module)) {
      relations.putValues(module, Collections.emptyList());
      boolean explicitJavaBase = false;
      for (PsiRequiresStatement statement : module.getRequires()) {
        PsiJavaModuleReference ref = statement.getModuleReference();
        if (ref != null) {
          if (JAVA_BASE.equals(ref.getCanonicalText())) explicitJavaBase = true;
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
        PsiJavaModule javaBase = JavaPsiFacade.getInstance(module.getProject()).findModule(JAVA_BASE, module.getResolveScope());
        if (javaBase != null) relations.putValue(module, javaBase);
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
  
  private static final class RequiresGraph {
    private final @NotNull Graph<PsiJavaModule> myGraph;
    private final @NotNull Set<String> myTransitiveEdges;

    private RequiresGraph(@NotNull Graph<PsiJavaModule> graph, @NotNull Set<String> transitiveEdges) {
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

    private @Nullable ModulePackageConflict findConflict(@NotNull PsiJavaModule source) {
      Map<String, PsiJavaModule> exports = new HashMap<>();
      return processExports(source, (pkg, m) -> {
        PsiJavaModule found = exports.put(pkg, m);
        return found == null ||
               found instanceof LightJavaModule && m instanceof LightJavaModule ||
               found.getName().equals(m.getName())
               ? null : new ModulePackageConflict(pkg, found, m);
      });
    }

    private @Nullable PsiJavaModule findOrigin(@NotNull PsiJavaModule module, @NotNull String packageName) {
      return processExports(module, (pkg, m) -> packageName.equals(pkg) ? m : null);
    }

    private <T> @Nullable T processExports(@NotNull PsiJavaModule start, @NotNull BiFunction<? super String, ? super PsiJavaModule, ? extends T> processor) {
      return myGraph.getNodes().contains(start) ? processExports(start.getName(), start, true, new HashSet<>(), processor) : null;
    }

    private <T> @Nullable T processExports(@Nullable String name,
                                           @NotNull PsiJavaModule module,
                                           boolean direct,
                                           @NotNull Set<? super PsiJavaModule> visited,
                                           @NotNull BiFunction<? super String, ? super PsiJavaModule, ? extends T> processor) {
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

    public static @NotNull String key(@NotNull PsiJavaModule module, @NotNull PsiJavaModule exporter) {
      return module.getName() + '/' + exporter.getName();
    }

    public @NotNull Set<PsiJavaModule> getAllDependencies(@NotNull PsiJavaModule module, boolean transitive) {
      Set<PsiJavaModule> requires = new HashSet<>();
      collectDependencies(module, requires, transitive);
      return requires;
    }

    private void collectDependencies(@NotNull PsiJavaModule module, @NotNull Set<PsiJavaModule> dependencies, boolean transitive) {
      for (Iterator<PsiJavaModule> iterator = myGraph.getIn(module); iterator.hasNext();) {
        PsiJavaModule dependency = iterator.next();
        if (!dependencies.contains(dependency) && (!transitive || myTransitiveEdges.contains(key(dependency, module)))) {
          dependencies.add(dependency);
          collectDependencies(dependency, dependencies, transitive);
        }
      }
    }
  }

  /**
   * State of package reference in module-info file
   */
  public enum PackageReferenceState {
    /**
     * Valid reference to a non-empty package
     */
    VALID,
    /**
     * No package is found
     */
    PACKAGE_NOT_FOUND,
    /**
     * Package exists but contains no classes (for exports) or no files (for opens)
     */
    PACKAGE_EMPTY
  }

  /**
   * @param statement statement to check ('opens' or 'exports' statement)
   * @return state of the reference
   */
  public static @NotNull PackageReferenceState checkPackageReference(@NotNull PsiPackageAccessibilityStatement statement) {
    PsiJavaCodeReferenceElement refElement = statement.getPackageReference();
    if (refElement != null) {
      PsiFile file = statement.getContainingFile();
      Module module = ModuleUtilCore.findModuleForFile(file);
      if (module != null) {
        PsiElement target = refElement.resolve();
        PsiDirectory[] directories = PsiDirectory.EMPTY_ARRAY;
        if (target instanceof PsiPackage psiPackage) {
          boolean inTests = ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(file.getVirtualFile());
          directories = psiPackage.getDirectories(module.getModuleScope(inTests));
          Module mainMultiReleaseModule = JavaMultiReleaseUtil.getMainMultiReleaseModule(module);
          if (mainMultiReleaseModule != null) {
            directories = ArrayUtil.mergeArrays(directories, psiPackage.getDirectories(mainMultiReleaseModule.getModuleScope(inTests)));
          }
        }
        String packageName = statement.getPackageName();
        if (directories.length == 0) {
          return PackageReferenceState.PACKAGE_NOT_FOUND;
        }
        boolean opens = statement.getRole() == PsiPackageAccessibilityStatement.Role.OPENS;
        if (packageName != null && isPackageEmpty(directories, packageName, opens)) {
          return PackageReferenceState.PACKAGE_EMPTY;
        }
      }
    }

    return PackageReferenceState.VALID;
  }
  
  private static boolean isPackageEmpty(PsiDirectory @NotNull [] directories, @NotNull String packageName, boolean anyFile) {
    if (anyFile) {
      return !ContainerUtil.exists(directories, dir -> dir.getFiles().length > 0);
    }
    else {
      return PsiUtil.isPackageEmpty(directories, packageName);
    }
  }

  /**
   * Helper service to support resolve in java-psi-impl
   */
  public static class Helper extends JavaModuleGraphHelper {
    @Contract("null->null")
    @Override
    public @Nullable PsiJavaModule findDescriptorByElement(@Nullable PsiElement element) {
      return JavaPsiModuleUtil.findDescriptorByElement(element);
    }
  
    @Override
    public @NotNull Set<PsiJavaModule> getAllTransitiveDependencies(@NotNull PsiJavaModule psiJavaModule) {
      return JavaPsiModuleUtil.getAllTransitiveDependencies(psiJavaModule);
    }

    @Override
    public boolean isAccessible(@NotNull String targetPackageName, PsiFile targetFile, @NotNull PsiElement place) {
      PsiFile useFile = place.getContainingFile() != null ? place.getContainingFile().getOriginalFile() : null;
      if (useFile == null) return true;
      List<JpmsModuleInfo.TargetModuleInfo> infos = JpmsModuleInfo.findTargetModuleInfos(targetPackageName, targetFile, useFile);
      if (infos == null) return true;
      return !infos.isEmpty() && ContainerUtil.exists(
        infos, info -> info.accessAt(useFile).checkAccess(useFile, JpmsModuleAccessInfo.JpmsModuleAccessMode.EXPORT) == null);
    }

    @Override
    public boolean isAccessible(@NotNull PsiJavaModule targetModule, @NotNull PsiElement place) {
      PsiFile useFile = place.getContainingFile() != null ? place.getContainingFile().getOriginalFile() : null;
      if (useFile == null) return true;
      return new JpmsModuleInfo.TargetModuleInfo(targetModule, "").accessAt(useFile).checkModuleAccess(place) == null;
    }
  }
}
