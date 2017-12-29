/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.util.importProject;

import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.impl.ProjectFromSourcesBuilderImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.StringInterner;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public abstract class ModuleInsight {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.importProject.ModuleInsight");
  @NotNull protected final ProgressIndicatorWrapper myProgress;

  private final Set<File> myEntryPointRoots = new HashSet<>();
  private final List<DetectedSourceRoot> mySourceRoots = new ArrayList<>();
  private final Set<String> myIgnoredNames = new HashSet<>();

  private final Map<File, Set<String>> mySourceRootToReferencedPackagesMap = new HashMap<>();
  private final Map<File, Set<String>> mySourceRootToPackagesMap = new HashMap<>();
  private final Map<File, Set<String>> myJarToPackagesMap = new HashMap<>();
  private final StringInterner myInterner = new StringInterner();

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

  public final void setRoots(final List<File> contentRoots, final List<? extends DetectedSourceRoot> sourceRoots, final Set<String> ignoredNames) {
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
      for (DetectedSourceRoot sourceRoot : processedRoots) {
        final File srcRoot = sourceRoot.getDirectory();
        final File moduleContentRoot = isEntryPointRoot(srcRoot) ? srcRoot : srcRoot.getParentFile();
        ModuleDescriptor moduleDescriptor = contentRootToModules.get(moduleContentRoot);
        if (moduleDescriptor != null) {
          moduleDescriptor.addSourceRoot(moduleContentRoot, sourceRoot);
        }
        else {
          moduleDescriptor = createModuleDescriptor(moduleContentRoot, Collections.singletonList(sourceRoot));
          contentRootToModules.put(moduleContentRoot, moduleDescriptor);
        }
      }

      buildModuleDependencies(contentRootToModules);

      myProgress.popState();
    }
    catch (ProcessCanceledException ignored) {
    }

    addModules(contentRootToModules.values());
  }

  protected void addExportedPackages(File sourceRoot, Set<String> packages) {
    mySourceRootToPackagesMap.put(sourceRoot, packages);
  }

  protected boolean isIgnoredName(File sourceRoot) {
    return myIgnoredNames.contains(sourceRoot.getName());
  }

  protected void addModules(Collection<ModuleDescriptor> newModules) {
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
        final String newName = suggestUniqueName(libNames, libJars.size() == 1? FileUtil.getNameWithoutExtension(libJars.iterator().next()) : library.getName());
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

  public LibraryDescriptor splitLibrary(LibraryDescriptor library, String newLibraryName, final Collection<File> jarsToExtract) {
    final LibraryDescriptor newLibrary = new LibraryDescriptor(newLibraryName, jarsToExtract);
    myLibraries.add(newLibrary);
    library.removeJars(jarsToExtract);
    if (library.getJars().size() == 0) {
      removeLibrary(library);
    }
    return newLibrary;
  }

  @Nullable
  public ModuleDescriptor splitModule(final ModuleDescriptor descriptor, String newModuleName, final Collection<File> contentsToExtract) {
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

  public void moveJarsToLibrary(final LibraryDescriptor from, Collection<File> files, LibraryDescriptor to) {
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
                                                                     @Nullable List<LibraryDescriptor> allLibraries) {
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


  private static List<LibraryDescriptor> buildInitialLibrariesLayout(final Set<File> jars) {
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

  private void scanSources(final File fromRoot, final String parentPackageName, final Set<String> usedPackages, final Set<String> selfPackages) {
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

  private void scanSourceFile(File file, final Set<String> usedPackages) {
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
