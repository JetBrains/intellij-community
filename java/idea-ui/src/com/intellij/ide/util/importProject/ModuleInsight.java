// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.importProject;

import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.impl.ProjectFromSourcesBuilderImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import com.intellij.util.text.StringFactory;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author Eugene Zhuravlev
 */
public abstract class ModuleInsight {
  private static final Logger LOG = Logger.getInstance(ModuleInsight.class);
  @NotNull protected final ProgressIndicatorWrapper myProgress;

  private final Set<File> myEntryPointRoots = new HashSet<>();
  private final List<DetectedSourceRoot> mySourceRoots = new ArrayList<>();
  private final Set<String> myIgnoredNames = new HashSet<>();

  private final Map<File, Set<String>> mySourceRootToReferencedPackagesMap = new HashMap<>();
  private final Map<File, Set<String>> mySourceRootToPackagesMap = new HashMap<>();
  private final Map<File, Set<String>> myJarToPackagesMap = new HashMap<>();
  private final Interner<String> myInterner = Interner.createStringInterner();

  private List<ModuleDescriptor> myModules;
  private List<LibraryDescriptor> myLibraries;
  private final Set<String> myExistingModuleNames;
  private final Set<String> myExistingProjectLibraryNames;

  public ModuleInsight(@Nullable final ProgressIndicator progress, Set<String> existingModuleNames, Set<String> existingProjectLibraryNames) {
    myExistingModuleNames = existingModuleNames;
    myExistingProjectLibraryNames = existingProjectLibraryNames;
    myProgress = new ProgressIndicatorWrapper(progress);
    setRoots(Collections.emptyList(), Collections.emptyList(), Collections.emptySet());
  }

  public final void setRoots(final List<? extends File> contentRoots, final List<? extends DetectedSourceRoot> sourceRoots, final Set<String> ignoredNames) {
    myModules = null;
    myLibraries = null;

    myEntryPointRoots.clear();
    myEntryPointRoots.addAll(contentRoots);

    mySourceRoots.clear();
    mySourceRoots.addAll(sourceRoots);

    myIgnoredNames.clear();
    myIgnoredNames.addAll(ignoredNames);

    myJarToPackagesMap.clear();
    myInterner.clear();
  }

  @Nullable
  public List<LibraryDescriptor> getSuggestedLibraries() {
    return myLibraries;
  }

  @Nullable
  public List<ModuleDescriptor> getSuggestedModules() {
    return myModules;
  }

  public void scanModules() {
    myProgress.setIndeterminate(true);
    final Map<File, ModuleDescriptor> contentRootToModules = new HashMap<>();

    try {
      myProgress.pushState();

      List<DetectedSourceRoot> processedRoots = new ArrayList<>();
      for (DetectedSourceRoot root : getSourceRootsToScan()) {
        final File sourceRoot = root.getDirectory();
        if (isIgnoredName(sourceRoot)) {
          continue;
        }
        myProgress.setText("Scanning " + sourceRoot.getPath());

        final HashSet<String> usedPackages = new HashSet<>();
        mySourceRootToReferencedPackagesMap.put(sourceRoot, usedPackages);

        final HashSet<String> selfPackages = new HashSet<>();
        addExportedPackages(sourceRoot, selfPackages);

        scanSources(sourceRoot, ProjectFromSourcesBuilderImpl.getPackagePrefix(root), usedPackages, selfPackages) ;
        usedPackages.removeAll(selfPackages);
        processedRoots.add(root);
      }
      myProgress.popState();

      myProgress.pushState();
      myProgress.setText("Building modules layout...");
      Map<File, ModuleCandidate> rootToModule = new HashMap<>();
      for (DetectedSourceRoot sourceRoot : processedRoots) {
        final File srcRoot = sourceRoot.getDirectory();
        final File moduleContentRoot = isEntryPointRoot(srcRoot) ? srcRoot : srcRoot.getParentFile();
        rootToModule.computeIfAbsent(moduleContentRoot, file -> new ModuleCandidate(moduleContentRoot)).myRoots.add(sourceRoot);
      }
      maximizeModuleFolders(rootToModule.values());
      for (Map.Entry<File, ModuleCandidate> entry : rootToModule.entrySet()) {
        File root = entry.getKey();
        ModuleCandidate module = entry.getValue();
        ModuleDescriptor moduleDescriptor = createModuleDescriptor(module.myFolder, module.myRoots);
        contentRootToModules.put(root, moduleDescriptor);
      }

      buildModuleDependencies(contentRootToModules);

      myProgress.popState();
    }
    catch (ProcessCanceledException ignored) {
    }

    addModules(contentRootToModules.values());
  }

  private static final class ModuleCandidate {
    final List<DetectedSourceRoot> myRoots = new ArrayList<>();
    @NotNull File myFolder;

    private ModuleCandidate(@NotNull File folder) {
      myFolder = folder;
    }
  }

  private void maximizeModuleFolders(@NotNull Collection<ModuleCandidate> modules) {
    TObjectIntHashMap<File> dirToChildRootCount = new TObjectIntHashMap<>();
    for (ModuleCandidate module : modules) {
      walkParents(module.myFolder, this::isEntryPointRoot, file -> {
        if (!dirToChildRootCount.adjustValue(file, 1)) {
          dirToChildRootCount.put(file, 1);
        }
      });
    }
    for (ModuleCandidate module : modules) {
      File moduleRoot = module.myFolder;
      Ref<File> adjustedRootRef = new Ref<>(module.myFolder);
      File current = moduleRoot;
      while (dirToChildRootCount.get(current) == 1) {
        adjustedRootRef.set(current);
        if (isEntryPointRoot(current)) break;
        current = current.getParentFile();
      }
      module.myFolder = adjustedRootRef.get();
    }
  }

  private static void walkParents(@NotNull File file, Predicate<File> stopCondition, @NotNull Consumer<File> fileConsumer) {
    File current = file;
    while (true) {
      fileConsumer.consume(current);
      if (stopCondition.test(current)) break;
      current = current.getParentFile();
    }
  }

  protected void addExportedPackages(File sourceRoot, Set<String> packages) {
    mySourceRootToPackagesMap.put(sourceRoot, packages);
  }

  protected boolean isIgnoredName(File sourceRoot) {
    return myIgnoredNames.contains(sourceRoot.getName());
  }

  protected void addModules(Collection<? extends ModuleDescriptor> newModules) {
    if (myModules == null) {
      myModules = new ArrayList<>(newModules);
    }
    else {
      myModules.addAll(newModules);
    }
    final Set<String> moduleNames = new HashSet<>(myExistingModuleNames);
    for (ModuleDescriptor module : newModules) {
      final String suggested = suggestUniqueName(moduleNames, module.getName());
      module.setName(suggested);
      moduleNames.add(suggested);
    }
  }

  @NotNull
  protected List<DetectedSourceRoot> getSourceRootsToScan() {
    return Collections.unmodifiableList(mySourceRoots);
  }

  protected boolean isEntryPointRoot(File srcRoot) {
    return myEntryPointRoots.contains(srcRoot);
  }

  protected abstract ModuleDescriptor createModuleDescriptor(final File moduleContentRoot, Collection<DetectedSourceRoot> sourceRoots);

  private void buildModuleDependencies(final Map<File, ModuleDescriptor> contentRootToModules) {
    final Set<File> moduleContentRoots = contentRootToModules.keySet();

    for (File contentRoot : moduleContentRoots) {
      final ModuleDescriptor checkedModule = contentRootToModules.get(contentRoot);
      myProgress.setText2("Building library dependencies for module " + checkedModule.getName());
      buildJarDependencies(checkedModule);

      myProgress.setText2("Building module dependencies for module " + checkedModule.getName());
      for (File aContentRoot : moduleContentRoots) {
        final ModuleDescriptor aModule = contentRootToModules.get(aContentRoot);
        if (checkedModule.equals(aModule)) {
          continue; // avoid self-dependencies
        }
        final Collection<? extends DetectedProjectRoot> aModuleRoots = aModule.getSourceRoots();
        checkModules:
        for (DetectedProjectRoot srcRoot: checkedModule.getSourceRoots()) {
          final Set<String> referencedBySourceRoot = mySourceRootToReferencedPackagesMap.get(srcRoot.getDirectory());
          for (DetectedProjectRoot aSourceRoot : aModuleRoots) {
            if (ContainerUtil.intersects(referencedBySourceRoot, mySourceRootToPackagesMap.get(aSourceRoot.getDirectory()))) {
              checkedModule.addDependencyOn(aModule);
              break checkModules;
            }
          }
        }
      }
    }
  }

  private void buildJarDependencies(final ModuleDescriptor module) {
    for (File jarFile : myJarToPackagesMap.keySet()) {
      final Set<String> jarPackages = myJarToPackagesMap.get(jarFile);
      for (DetectedProjectRoot srcRoot : module.getSourceRoots()) {
        if (ContainerUtil.intersects(mySourceRootToReferencedPackagesMap.get(srcRoot.getDirectory()), jarPackages)) {
          module.addLibraryFile(jarFile);
          break;
        }
      }
    }
  }

  public void scanLibraries() {
    myProgress.setIndeterminate(true);
    myProgress.pushState();
    try {
      try {
        for (File root : myEntryPointRoots) {
          myProgress.setText("Scanning for libraries " + root.getPath());
          scanRootForLibraries(root);
        }
      }
      catch (ProcessCanceledException ignored) {
      }
      myProgress.setText("Building initial libraries layout...");
      final List<LibraryDescriptor> libraries = buildInitialLibrariesLayout(myJarToPackagesMap.keySet());
      // correct library names so that there are no duplicates
      final Set<String> libNames = new HashSet<>(myExistingProjectLibraryNames);
      for (LibraryDescriptor library : libraries) {
        final Collection<File> libJars = library.getJars();
        final String newName = suggestUniqueName(libNames, libJars.size() == 1 ? FileUtilRt
          .getNameWithoutExtension(libJars.iterator().next().getName()) : library.getName());
        library.setName(newName);
        libNames.add(newName);
      }
      myLibraries = libraries;
    }
    finally {
      myProgress.popState();
    }
  }

  public abstract boolean isApplicableRoot(final DetectedProjectRoot root);

  private static String suggestUniqueName(Set<String> existingNames, String baseName) {
    String name = baseName;
    int index = 1;
    while (existingNames.contains(name)) {
      name = baseName + (index++);
    }
    return name;
  }

  public void merge(final ModuleDescriptor mainModule, final ModuleDescriptor module) {
    for (File contentRoot : module.getContentRoots()) {
      final File _contentRoot = appendContentRoot(mainModule, contentRoot);
      final Collection<DetectedSourceRoot> sources = module.getSourceRoots(contentRoot);
      for (DetectedSourceRoot source : sources) {
        mainModule.addSourceRoot(_contentRoot, source);
      }
    }
    for (File jar : module.getLibraryFiles()) {
      mainModule.addLibraryFile(jar);
    }
    // fix forward dependencies
    for (ModuleDescriptor dependency : module.getDependencies()) {
      if (!mainModule.equals(dependency)) { // avoid self-dependencies
        mainModule.addDependencyOn(dependency);
      }
    }

    myModules.remove(module);
    // fix back dependencies
    for (ModuleDescriptor moduleDescr : myModules) {
      if (moduleDescr.getDependencies().contains(module)) {
        moduleDescr.removeDependencyOn(module);
        if (!moduleDescr.equals(mainModule)) { // avoid self-dependencies
          moduleDescr.addDependencyOn(mainModule);
        }
      }
    }
  }

  public LibraryDescriptor splitLibrary(LibraryDescriptor library, String newLibraryName, final Collection<? extends File> jarsToExtract) {
    final LibraryDescriptor newLibrary = new LibraryDescriptor(newLibraryName, new ArrayList<>(jarsToExtract));
    myLibraries.add(newLibrary);
    library.removeJars(jarsToExtract);
    if (library.getJars().size() == 0) {
      removeLibrary(library);
    }
    return newLibrary;
  }

  @Nullable
  public ModuleDescriptor splitModule(final ModuleDescriptor descriptor, String newModuleName, final Collection<? extends File> contentsToExtract) {
    ModuleDescriptor newModule = null;
    for (File root : contentsToExtract) {
      final Collection<DetectedSourceRoot> sources = descriptor.removeContentRoot(root);
      if (newModule == null) {
        newModule = createModuleDescriptor(root, sources != null ? sources : new HashSet<>());
      }
      else {
        if (sources != null && sources.size() > 0) {
          for (DetectedSourceRoot source : sources) {
            newModule.addSourceRoot(root, source);
          }
        }
        else {
          newModule.addContentRoot(root);
        }
      }
    }

    if (newModule != null) {
      newModule.setName(newModuleName);
      myModules.add(newModule);
    }
    else {
      return null;
    }

    final Map<File, ModuleDescriptor> contentRootToModule = new HashMap<>();
    for (ModuleDescriptor module : myModules) {
      final Set<File> roots = module.getContentRoots();
      for (File root : roots) {
        contentRootToModule.put(root, module);
      }
      module.clearModuleDependencies();
      module.clearLibraryFiles();
    }

    buildModuleDependencies(contentRootToModule);
    return newModule;
  }

  public void removeLibrary(LibraryDescriptor lib) {
    myLibraries.remove(lib);
  }

  public void moveJarsToLibrary(final LibraryDescriptor from, Collection<? extends File> files, LibraryDescriptor to) {
    to.addJars(files);
    from.removeJars(files);
    // remove the library if it became empty
    if (from.getJars().size() == 0) {
      removeLibrary(from);
    }
  }

  public Collection<LibraryDescriptor> getLibraryDependencies(ModuleDescriptor module) {
    return getLibraryDependencies(module, myLibraries);
  }

  public static Collection<LibraryDescriptor> getLibraryDependencies(ModuleDescriptor module,
                                                                     @Nullable List<? extends LibraryDescriptor> allLibraries) {
    final Set<LibraryDescriptor> libs = new HashSet<>();
    if (allLibraries != null) {
      for (LibraryDescriptor library : allLibraries) {
        if (ContainerUtil.intersects(library.getJars(), module.getLibraryFiles())) {
          libs.add(library);
        }
      }
    }
    return libs;
  }

  private static File appendContentRoot(final ModuleDescriptor module, final File contentRoot) {
    final Set<File> moduleRoots = module.getContentRoots();
    for (File moduleRoot : moduleRoots) {
      if (FileUtil.isAncestor(moduleRoot, contentRoot, false)) {
        return moduleRoot; // no need to include a separate root
      }
      if (FileUtil.isAncestor(contentRoot, moduleRoot, true)) {
        final Collection<DetectedSourceRoot> currentSources = module.getSourceRoots(moduleRoot);
        module.removeContentRoot(moduleRoot);
        module.addContentRoot(contentRoot);
        for (DetectedSourceRoot source : currentSources) {
          module.addSourceRoot(contentRoot, source);
        }
        return contentRoot; // no need to include a separate root
      }
    }
    module.addContentRoot(contentRoot);
    return contentRoot;
  }


  private static List<LibraryDescriptor> buildInitialLibrariesLayout(final Set<? extends File> jars) {
    final Map<File, LibraryDescriptor> rootToLibraryMap = new HashMap<>();
    for (File jar : jars) {
      final File parent = jar.getParentFile();
      LibraryDescriptor lib = rootToLibraryMap.get(parent);
      if (lib == null) {
        lib = new LibraryDescriptor(parent.getName(), new HashSet<>());
        rootToLibraryMap.put(parent, lib);
      }
      lib.addJars(Collections.singleton(jar));
    }
    return new ArrayList<>(rootToLibraryMap.values());
  }

  private void scanSources(final File fromRoot, final String parentPackageName, final Set<? super String> usedPackages, final Set<? super String> selfPackages) {
    if (isIgnoredName(fromRoot)) {
      return;
    }
    final File[] files = fromRoot.listFiles();
    if (files != null) {
      myProgress.checkCanceled();
      boolean includeParentName = false;
      for (File file : files) {
        if (file.isDirectory()) {
          String subPackageName = parentPackageName + (parentPackageName.isEmpty() ? "" : ".") + file.getName();
          scanSources(file, subPackageName, usedPackages, selfPackages);
        }
        else {
          if (isSourceFile(file)) {
            includeParentName = true;
            scanSourceFile(file, usedPackages);
          }
        }
      }
      if (includeParentName) {
        selfPackages.add(myInterner.intern(parentPackageName));
      }
    }
  }

  protected abstract boolean isSourceFile(final File file);

  private void scanSourceFile(File file, final Set<? super String> usedPackages) {
    myProgress.setText2(file.getName());
    try {
      final char[] chars = FileUtil.loadFileText(file);
      scanSourceFileForImportedPackages(StringFactory.createShared(chars), s -> usedPackages.add(myInterner.intern(s)));
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  protected abstract void scanSourceFileForImportedPackages(final CharSequence chars, Consumer<String> result);

  private void scanRootForLibraries(File fromRoot) {
    if (isIgnoredName(fromRoot)) {
      return;
    }
    final File[] files = fromRoot.listFiles();
    if (files != null) {
      myProgress.checkCanceled();
      for (File file : files) {
        if (file.isDirectory()) {
          scanRootForLibraries(file);
        }
        else {
          final String fileName = file.getName();
          if (isLibraryFile(fileName)) {
            if (!myJarToPackagesMap.containsKey(file)) {
              final HashSet<String> libraryPackages = new HashSet<>();
              myJarToPackagesMap.put(file, libraryPackages);

              myProgress.pushState();
              myProgress.setText2(file.getName());
              try {
                scanLibraryForDeclaredPackages(file, s -> {
                  if (!libraryPackages.contains(s)) {
                    libraryPackages.add(myInterner.intern(s));
                  }
                });
              }
              catch (IOException e) {
                LOG.info(e);
              }
              catch (IllegalArgumentException e) { // may be thrown from java.util.zip.ZipCoder.toString for corrupted archive
                LOG.info(e);
              }
              catch (InternalError e) { // indicates that file is somehow damaged and cannot be processed
                LOG.info(e);
              }
              finally {
                myProgress.popState();
              }
            }
          }
        }
      }
    }
  }

  protected abstract boolean isLibraryFile(final String fileName);

  protected abstract void scanLibraryForDeclaredPackages(File file, Consumer<String> result) throws IOException;

}
