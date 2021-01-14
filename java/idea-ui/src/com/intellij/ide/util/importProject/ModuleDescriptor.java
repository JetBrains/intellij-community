// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.importProject;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
*/
public class ModuleDescriptor {
  private String myName;
  private final MultiMap<File, DetectedSourceRoot> myContentToSourceRoots = new MultiMap<>();
  private final Set<File> myLibraryFiles = new HashSet<>();
  private final Set<ModuleDescriptor> myDependencies = new HashSet<>();
  private static final Set<String> ourModuleNameStopList = CollectionFactory.createCaseInsensitiveStringSet(
    Arrays.asList("java", "src", "source", "sources", "C:", "D:", "E:", "F:", "temp", "tmp")
  );

  private boolean myReuseExistingElement;
  private final List<ModuleBuilder.ModuleConfigurationUpdater> myConfigurationUpdaters = new SmartList<>();
  private final ModuleType<?> myModuleType;

  public ModuleDescriptor(final File contentRoot, final ModuleType moduleType, final Collection<? extends DetectedSourceRoot> sourceRoots) {
    myName = suggestModuleName(contentRoot);
    myContentToSourceRoots.putValues(contentRoot, sourceRoots);
    myModuleType = moduleType;
  }

  public ModuleDescriptor(final File contentRoot, final ModuleType moduleType,
                          final DetectedSourceRoot sourceRoot) {
    this(contentRoot, moduleType, Collections.singletonList(sourceRoot));
  }

  public void reuseExisting(boolean reuseExistingElement) {
    myReuseExistingElement = reuseExistingElement;
  }

  public void addConfigurationUpdater(ModuleBuilder.ModuleConfigurationUpdater updater) {
    myConfigurationUpdaters.add(updater);
  }

  public void updateModuleConfiguration(Module module, ModifiableRootModel rootModel) {
    for (ModuleBuilder.ModuleConfigurationUpdater updater : myConfigurationUpdaters) {
      updater.update(module, rootModel);
    }
  }

  public boolean isReuseExistingElement() {
    return myReuseExistingElement;
  }

  public ModuleType getModuleType() {
    return myModuleType;
  }

  private static String suggestModuleName(final File contentRoot) {
    for (File dir = contentRoot; dir != null; dir = dir.getParentFile()) {
      final String suggestion = dir.getName();
      if (!ourModuleNameStopList.contains(suggestion)) {
        return suggestion;
      }
    }

    return contentRoot.getName();
  }

  public @NlsSafe String getName() {
    return myName;
  }

  public void setName(final String name) {
    myName = name;
  }

  public Set<File> getContentRoots() {
    return Collections.unmodifiableSet(myContentToSourceRoots.keySet());
  }

  public Collection<? extends DetectedProjectRoot> getSourceRoots() {
    return myContentToSourceRoots.values();
  }

  public Collection<DetectedSourceRoot> getSourceRoots(File contentRoot) {
    return myContentToSourceRoots.get(contentRoot);
  }

  public void addContentRoot(File contentRoot) {
    myContentToSourceRoots.put(contentRoot, new HashSet<>());
  }

  public Collection<DetectedSourceRoot> removeContentRoot(File contentRoot) {
    return myContentToSourceRoots.remove(contentRoot);
  }

  public void addSourceRoot(final File contentRoot, DetectedSourceRoot sourceRoot) {
    myContentToSourceRoots.putValue(contentRoot, sourceRoot);
  }

  public void addDependencyOn(ModuleDescriptor dependence) {
    myDependencies.add(dependence);
  }

  public void removeDependencyOn(ModuleDescriptor module) {
    myDependencies.remove(module);
  }

  public void addLibraryFile(File libFile) {
    myLibraryFiles.add(libFile);
  }

  public Set<File> getLibraryFiles() {
    return myLibraryFiles;
  }

  public Set<ModuleDescriptor> getDependencies() {
    return Collections.unmodifiableSet(myDependencies);
  }

  /**
   * For debug purposes only
   */
  public String toString() {
    @NonNls final StringBuilder builder = new StringBuilder();
    builder.append("[Module: ").append(getContentRoots()).append(" | ");
    for (DetectedProjectRoot sourceRoot : getSourceRoots()) {
      builder.append(sourceRoot.getDirectory().getName()).append(",");
    }
    builder.append("]");
    return builder.toString();
  }

  public void clearModuleDependencies() {
    myDependencies.clear();
  }

  public void clearLibraryFiles() {
    myLibraryFiles.clear();
  }

  @NotNull
  public String computeModuleFilePath() throws InvalidDataException {
    final String name = getName();
    final Set<File> contentRoots = getContentRoots();
    if (contentRoots.size() > 0) {
      return contentRoots.iterator().next().getPath() + File.separator + name + ModuleFileType.DOT_DEFAULT_EXTENSION;
    }
    else {
      throw new InvalidDataException("Module " + name + " has no content roots and will not be created.");
    }
  }
}
