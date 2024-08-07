// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.JvmLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiJavaModuleModificationTracker;
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.*;
import java.util.function.BiFunction;
import java.util.jar.JarFile;

import static com.intellij.openapi.roots.DependencyScope.PROVIDED;
import static com.intellij.psi.PsiJavaModule.JAVA_BASE;

public final class JavaModuleGraphUtil {
  private static final Set<String> STATIC_REQUIRES_MODULE_NAMES = Set.of("lombok");

  private JavaModuleGraphUtil() { }

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

  @Contract("null,_->null")
  public static @Nullable PsiJavaModule findDescriptorByFile(@Nullable VirtualFile file, @NotNull Project project) {
    if (file == null) return null;
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    return index.isInLibrary(file)
           ? findDescriptorInLibrary(project, index, file)
           : findDescriptorByModule(index.getModuleForFile(file), index.isInTestSourceContent(file));
  }

  @Nullable
  private static PsiJavaModule findDescriptorInLibrary(@NotNull Project project, @NotNull ProjectFileIndex index, @NotNull VirtualFile file) {
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

  @Contract("null,_->null")
  public static @Nullable PsiJavaModule findDescriptorByModule(@Nullable Module module, boolean inTests) {
    if (module == null) return null;
    CachedValuesManager valuesManager = CachedValuesManager.getManager(module.getProject());
    PsiJavaModule javaModule = inTests //to have different providers for production and tests
                               ? valuesManager.getCachedValue(module, () -> createModuleCacheResult(module, true))
                               : valuesManager.getCachedValue(module, () -> createModuleCacheResult(module, false));
    return javaModule != null && javaModule.isValid() ? javaModule : null;
  }

  public static @Nullable PsiJavaModule findDescriptorByLibrary(@Nullable Library library, @NotNull Project project) {
    if (library == null) return null;
    final VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
    if (files.length == 0) return null;
    final PsiJavaModule javaModule = findDescriptorInLibrary(project, ProjectFileIndex.getInstance(project), files[0]);
    return javaModule != null && javaModule.isValid() ? javaModule : null;
  }

  public static @Nullable PsiJavaModule findNonAutomaticDescriptorByModule(@Nullable Module module, boolean inTests) {
    PsiJavaModule javaModule = findDescriptorByModule(module, inTests);
    return javaModule instanceof LightJavaModule ? null : javaModule;
  }

  @NotNull
  private static Result<PsiJavaModule> createModuleCacheResult(@NotNull Module module,
                                                               boolean inTests) {
    Project project = module.getProject();
    return new Result<>(findDescriptionByModuleInner(module, inTests),
                        ProjectRootModificationTracker.getInstance(project),
                        PsiJavaModuleModificationTracker.getInstance(project));
  }

  @Nullable
  private static PsiJavaModule findDescriptionByModuleInner(@NotNull Module module, boolean inTests) {
    Project project = module.getProject();
    GlobalSearchScope moduleScope = module.getModuleScope();
    String virtualAutoModuleName = ManifestUtil.lightManifestAttributeValue(module, PsiJavaModule.AUTO_MODULE_NAME);
    if (!DumbService.isDumb(project) &&
        FilenameIndex.getVirtualFilesByName(PsiJavaModule.MODULE_INFO_FILE, moduleScope).isEmpty() &&
        FilenameIndex.getVirtualFilesByName("MANIFEST.MF", moduleScope).isEmpty() &&
        virtualAutoModuleName == null) {
      return null;
    }
    JavaSourceRootType rootType = inTests ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    List<VirtualFile> sourceRoots = rootManager.getSourceRoots(rootType);
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
          return Result.create(result, manifestPsi, ProjectRootModificationTracker.getInstance(project));
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

  public static @NotNull Collection<PsiJavaModule> findCycle(@NotNull PsiJavaModule module) {
    Project project = module.getProject();
    List<Set<PsiJavaModule>> cycles = CachedValuesManager.getManager(project).getCachedValue(project, () ->
      Result.create(findCycles(project),
                    PsiJavaModuleModificationTracker.getInstance(project),
                    ProjectRootModificationTracker.getInstance(project)));
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
    return getRequiresGraph(source).getAllDependencies(source, false);
  }

  public static @NotNull Set<PsiJavaModule> getAllTransitiveDependencies(PsiJavaModule source) {
    return getRequiresGraph(source).getAllDependencies(source, true);
  }

  public static @Nullable Trinity<String, PsiJavaModule, PsiJavaModule> findConflict(@NotNull PsiJavaModule module) {
    return getRequiresGraph(module).findConflict(module);
  }

  public static @Nullable PsiJavaModule findOrigin(@NotNull PsiJavaModule module, @NotNull String packageName) {
    return getRequiresGraph(module).findOrigin(module, packageName);
  }

  /**
   * Determines if a specified module is readable from a given context
   *
   * @param place            current module/position
   * @param targetModuleFile file from the target module
   * @return {@code true} if the target module is readable from the place; {@code false} otherwise.
   */
  public static boolean isModuleReadable(@NotNull PsiElement place,
                                         @NotNull VirtualFile targetModuleFile) {
    PsiJavaModule targetModule = findDescriptorByFile(targetModuleFile, place.getProject());
    if (targetModule == null) return true;
    return isModuleReadable(place, targetModule);
  }

  /**
   * Determines if the specified modules are readable from a given context.
   *
   * @param place        the current position or element from where readability is being checked
   * @param targetModule the target module to check readability against
   * @return {@code true} if any of the target modules are readable from the current context; {@code false} otherwise
   */
  public static boolean isModuleReadable(@NotNull PsiElement place,
                                         @NotNull PsiJavaModule targetModule) {
    return ContainerUtil.and(JavaModuleSystem.EP_NAME.getExtensionList(), sys -> sys.isAccessible(targetModule, place));
  }

  public static boolean addDependency(@NotNull PsiJavaModule from,
                                      @NotNull String to,
                                      @Nullable DependencyScope scope,
                                      boolean isExported) {
    if (to.equals(JAVA_BASE)) return false;
    if (!PsiUtil.isAvailable(JavaFeature.MODULES, from)) return false;
    if (from instanceof LightJavaModule) return false;
    if (to.equals(from.getName())) return false;
    if (!PsiNameHelper.isValidModuleName(to, from)) return false;
    if (alreadyContainsRequires(from, to)) return false;
    PsiUtil.addModuleStatement(from, PsiKeyword.REQUIRES + " " +
                                     (isStaticModule(to, scope) ? PsiKeyword.STATIC + " " : "") +
                                     (isExported ? PsiKeyword.TRANSITIVE + " " : "") +
                                     to);
    PsiJavaModule toModule = findDependencyByName(from, to);
    if (toModule != null) optimizeDependencies(from, toModule);
    return true;
  }

  @Nullable
  private static PsiJavaModule findDependencyByName(@NotNull PsiJavaModule module, @NotNull String name) {
    for (PsiRequiresStatement require : module.getRequires()) {
      if (name.equals(require.getModuleName())) return require.resolve();
    }
    return null;
  }

  public static boolean addDependency(@NotNull PsiElement from,
                                      @NotNull PsiClass to,
                                      @Nullable DependencyScope scope) {
    if (!PsiUtil.isAvailable(JavaFeature.MODULES, from)) return false;
    PsiJavaModule fromDescriptor = findDescriptorByElement(from);
    if (fromDescriptor == null) return false;
    PsiJavaModule toDescriptor = findDescriptorByElement(to);
    if (toDescriptor == null) return false;
    if(!ContainerUtil.and(JavaModuleSystem.EP_NAME.getExtensionList(), sys -> sys.isAccessible(to, from))) return false;
    return addDependency(fromDescriptor, toDescriptor, scope);
  }

  public static boolean addDependency(@NotNull PsiJavaModule from,
                                      @NotNull PsiJavaModule to,
                                      @Nullable DependencyScope scope) {
    if (to.getName().equals(JAVA_BASE)) return false;
    if (!PsiUtil.isAvailable(JavaFeature.MODULES, from)) return false;
    if (from instanceof LightJavaModule) return false;
    if (from == to) return false;
    if (!PsiNameHelper.isValidModuleName(to.getName(), to)) return false;
    if (contains(from.getRequires(), to.getName())) return false;
    if (reads(from, to)) return false;
    PsiUtil.addModuleStatement(from, PsiKeyword.REQUIRES + " " +
                                     (isStaticModule(to.getName(), scope) ? PsiKeyword.STATIC + " " : "") +
                                     (isExported(from, to) ? PsiKeyword.TRANSITIVE + " " : "") +
                                     to.getName());
    optimizeDependencies(from, to);
    return true;
  }

  private static boolean contains(@NotNull Iterable<PsiRequiresStatement> requires, @NotNull String name) {
    for (PsiRequiresStatement statement : requires) {
      if (name.equals(statement.getModuleName())) return true;
    }
    return false;
  }

  /**
   * Optimizes the dependencies of a current module file by removing redundant 'requires' statements
   * that contain (transitive) in the selected dependency.
   *
   * @param currentModule      The Java module for which to optimize the dependencies.
   * @param selectedDependency The Java module that is the selected dependency.
   */
  public static void optimizeDependencies(@NotNull PsiJavaModule currentModule, @NotNull PsiJavaModule selectedDependency) {
    Map<PsiJavaModule, PsiRequiresStatement> requires = new HashMap<>();
    for (PsiRequiresStatement require : currentModule.getRequires()) {
      PsiJavaModule resolvedModule = require.resolve();
      if (resolvedModule != null) {
        requires.put(resolvedModule, require);
      }
    }

    Set<PsiJavaModule> redundant = new HashSet<>();
    for (PsiJavaModule module : requires.keySet()) {
      if (module.getName().equals(selectedDependency.getName())) continue;
      if (reads(selectedDependency, module)) redundant.add(module);
    }

    for (PsiJavaModule module : redundant) {
      requires.get(module).delete();
    }
  }

  private static boolean isExported(@NotNull PsiJavaModule from, @NotNull PsiJavaModule to) {
    VirtualFile toFile = getVirtualFile(to);
    if (toFile == null) return false;

    Module fromModule = ModuleUtilCore.findModuleForPsiElement(from);
    if (fromModule == null) return false;

    Set<OrderEntry> toEntries = new HashSet<>(ProjectFileIndex.getInstance(from.getProject())
                                                .getOrderEntriesForFile(toFile));
    if (toEntries.isEmpty()) return false;

    OrderEntry[] entries = ModuleRootManager.getInstance(fromModule).getOrderEntries();
    for (OrderEntry entry : entries) {
      if (toEntries.contains(entry) && entry instanceof ExportableOrderEntry exportable) {
        return exportable.isExported();
      }
    }
    return false;
  }

  @Nullable
  private static VirtualFile getVirtualFile(@NotNull PsiJavaModule module) {
    if (module instanceof LightJavaModule light) {
      return light.getRootVirtualFile();
    }
    return PsiUtilCore.getVirtualFile(module);
  }

  private static boolean alreadyContainsRequires(@NotNull PsiJavaModule module, @NotNull String dependency) {
    for (PsiRequiresStatement requiresStatement : module.getRequires()) {
      if (Objects.equals(requiresStatement.getModuleName(), dependency)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isStaticModule(@NotNull String moduleName, @Nullable DependencyScope scope) {
    if (STATIC_REQUIRES_MODULE_NAMES.contains(moduleName)) return true;
    return scope == PROVIDED;
  }

  /*
   * Looks for cycles between Java modules in the project sources.
   * Library/JDK modules are excluded in an assumption there can't be any lib -> src dependencies.
   * Module references are resolved "globally" (i.e., without taking project dependencies into account).
   */
  private static @NotNull List<Set<PsiJavaModule>> findCycles(@NotNull Project project) {
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

  @NotNull
  private static Map<String, Set<String>> exportsMap(@NotNull PsiJavaModule source) {
    Map<String, Set<String>> map = new HashMap<>();
    for (PsiPackageAccessibilityStatement statement : source.getExports()) {
      String pkg = statement.getPackageName();
      List<String> targets = statement.getModuleNames();
      map.put(pkg, targets.isEmpty() ? Collections.emptySet() : new HashSet<>(targets));
    }
    return map;
  }

  private static RequiresGraph getRequiresGraph(@NotNull PsiJavaModule module) {
    final Project project = module.getProject();
    if (DumbService.getInstance(project).isAlternativeResolveEnabled()) {
      return DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> buildRequiresGraph(project));
    }
    return CachedValuesManager.getManager(project).getCachedValue(project, () ->
      Result.create(buildRequiresGraph(project),
                    PsiJavaModuleModificationTracker.getInstance(project),
                    ProjectRootModificationTracker.getInstance(project)));
  }

  /*
   * Collects all module dependencies in the project.
   * The resulting graph is used for tracing readability and checking package conflicts.
   */
  @NotNull
  private static RequiresGraph buildRequiresGraph(@NotNull Project project) {
    MultiMap<PsiJavaModule, PsiJavaModule> relations = MultiMap.create();
    Set<String> transitiveEdges = new HashSet<>();

    JavaModuleNameIndex index = JavaModuleNameIndex.getInstance();
    GlobalSearchScope scope = ProjectScope.getAllScope(project);
    for (String key : index.getAllKeys(project)) {
      for (PsiJavaModule module : index.getModules(key, project, scope)) {
        visit(module, relations, transitiveEdges);
      }
    }

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

  private static final class RequiresGraph {
    @NotNull private final Graph<PsiJavaModule> myGraph;
    @NotNull private final Set<String> myTransitiveEdges;

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

    public @Nullable Trinity<String, PsiJavaModule, PsiJavaModule> findConflict(@NotNull PsiJavaModule source) {
      Map<String, PsiJavaModule> exports = new HashMap<>();
      return processExports(source, (pkg, m) -> {
        PsiJavaModule found = exports.put(pkg, m);
        return found == null ||
               found instanceof LightJavaModule && m instanceof LightJavaModule ||
               found.getName().equals(m.getName())
               ? null : new Trinity<>(pkg, found, m);
      });
    }

    public @Nullable PsiJavaModule findOrigin(@NotNull PsiJavaModule module, @NotNull String packageName) {
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

    @NotNull
    public static String key(@NotNull PsiJavaModule module, @NotNull PsiJavaModule exporter) {
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
    @NotNull private final MultiMap<String, PsiJavaModule> myModules;
    private final boolean myIncludeLibraries;
    private final boolean myIsInTests;

    private JavaModuleScope(@NotNull Project project, @NotNull Set<PsiJavaModule> modules) {
      super(project);
      myModules = new MultiMap<>();
      for (PsiJavaModule module : modules) {
        myModules.putValue(module.getName(), module);
      }
      ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
      myIncludeLibraries = ContainerUtil.or(modules, m -> {
        PsiFile containingFile = m.getContainingFile();
        if (containingFile == null) return true;
        VirtualFile moduleFile = containingFile.getVirtualFile();
        if (moduleFile == null) return true;
        return fileIndex.isInLibrary(moduleFile);
      });
      myIsInTests = !myIncludeLibraries && ContainerUtil.or(modules, m -> {
        PsiFile containingFile = m.getContainingFile();
        if (containingFile == null) return true;
        VirtualFile moduleFile = containingFile.getVirtualFile();
        if (moduleFile == null) return true;
        return fileIndex.isInTestSourceContent(moduleFile);
      });
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return contains(findDescriptorByModule(aModule, myIsInTests));
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
      ProjectFileIndex index = ProjectFileIndex.getInstance(project);
      if (index.isInLibrary(file)) return myIncludeLibraries && contains(findDescriptorInLibrary(project, index, file));
      Module module = index.getModuleForFile(file);
      return contains(findDescriptorByModule(module, myIsInTests));
    }

    private boolean contains(@Nullable PsiJavaModule module) {
      if (module == null || !module.isValid()) return false;
      Collection<PsiJavaModule> myCollectedModules = myModules.get(module.getName());
      return myCollectedModules.contains(module);
    }

    private static boolean isJvmLanguageFile(@NotNull VirtualFile file) {
      FileTypeRegistry fileTypeRegistry = FileTypeRegistry.getInstance();
      FileType fileType = fileTypeRegistry.getFileTypeByFileName(file.getName());
      if (fileType == JavaClassFileType.INSTANCE ||
          fileType == JavaFileType.INSTANCE) {
        return true;
      }
      LanguageFileType languageFileType = ObjectUtils.tryCast(fileType, LanguageFileType.class);
      if(languageFileType == null) return false;
      Language language = languageFileType.getLanguage();
      return language.isKindOf(JavaLanguage.INSTANCE) ||
             language instanceof JvmLanguage ||
             language.getID().equals("kotlin");
    }

    public static @Nullable JavaModuleScope moduleScope(@NotNull PsiJavaModule module) {
      PsiFile moduleFile = module.getContainingFile();
      if (moduleFile == null) return null;
      VirtualFile virtualFile = moduleFile.getVirtualFile();
      if (virtualFile == null) return null;
      return new JavaModuleScope(module.getProject(), Set.of(module));
    }

    /**
     * Creates a JavaModuleScope that includes the given module and all transitive modules.
     *
     * @param module the base PsiJavaModule for which to create the scope, must not be null
     * @return a new JavaModuleScope including all transitive modules of the given module, or null if the moduleFile is null or no transitive modules are found
     */
    public static @Nullable JavaModuleScope moduleWithTransitiveScope(@NotNull PsiJavaModule module) {
      Set<PsiJavaModule> allModules = JavaResolveUtil.getAllTransitiveModulesIncludeCurrent(module);
      if (allModules.isEmpty()) return null;
      return new JavaModuleScope(module.getProject(), allModules);
    }
  }
}
