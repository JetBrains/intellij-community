/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.make.BuildConfiguration;
import com.intellij.openapi.compiler.make.BuildParticipant;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.deployment.DeploymentUtilImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class ProcessingItemsBuilderContext {
  private final Map<VirtualFile, PackagingProcessingItem> myItemsBySource;
  private final Map<String, VirtualFile> mySourceByOutput;
  private final Map<BuildConfiguration, JarInfo> myCachedJarForConfiguration;
  private final Map<VirtualFile, JarInfo> myCachedJarForDir;
  private final Map<ExplodedDestinationInfo, BuildParticipant> myDestinationOwners;
  private MultiValuesMap<Module, PackagingProcessingItem> myItemsByModule;
  private final CompileContext myCompileContext;
  private final List<ManifestFileInfo> myManifestFiles;

  public ProcessingItemsBuilderContext(final CompileContext compileContext) {
    myCompileContext = compileContext;
    myManifestFiles = new ArrayList<ManifestFileInfo>();
    myItemsBySource = new HashMap<VirtualFile, PackagingProcessingItem>();
    mySourceByOutput = new HashMap<String, VirtualFile>();
    myCachedJarForDir = new HashMap<VirtualFile, JarInfo>();
    myCachedJarForConfiguration = new HashMap<BuildConfiguration, JarInfo>();
    myDestinationOwners = new HashMap<ExplodedDestinationInfo, BuildParticipant>();
    myItemsByModule = new MultiValuesMap<Module, PackagingProcessingItem>();
  }

  public List<ManifestFileInfo> getManifestFiles() {
    return myManifestFiles;
  }

  public PackagingProcessingItem[] getProcessingItems() {
    final Collection<PackagingProcessingItem> processingItems = myItemsBySource.values();
    return processingItems.toArray(new PackagingProcessingItem[processingItems.size()]);
  }

  public PackagingProcessingItem[] getProcessingItems(Module[] modules) {
    List<PackagingProcessingItem> items = new ArrayList<PackagingProcessingItem>();
    for (Module module : modules) {
      Collection<PackagingProcessingItem> itemsByModule = myItemsByModule.get(module);
      if (itemsByModule != null) {
        items.addAll(itemsByModule);
      }
    }
    return items.toArray(new PackagingProcessingItem[items.size()]);
  }

  public boolean checkOutputPath(final String outputPath, final VirtualFile sourceFile) {
    VirtualFile old = mySourceByOutput.get(outputPath);
    if (old == null) {
      mySourceByOutput.put(outputPath, sourceFile);
      return true;
    }
    //todo[nik] show warning?
    return false;
  }

  public Map<VirtualFile, PackagingProcessingItem> getItemsBySourceMap() {
    return myItemsBySource;
  }

  @Nullable
  public VirtualFile getSourceByOutput(String outputPath) {
    return mySourceByOutput.get(outputPath);
  }

  public PackagingProcessingItem getOrCreateProcessingItem(final VirtualFile sourceFile, BuildParticipant participant) {
    PackagingProcessingItem item = myItemsBySource.get(sourceFile);
    if (item == null) {
      item = new PackagingProcessingItem(sourceFile);
      myItemsBySource.put(sourceFile, item);
    }
    myItemsByModule.put(participant.getModule(), item);
    return item;
  }

  public JarInfo getCachedJar(final VirtualFile sourceFile) {
    return myCachedJarForDir.get(sourceFile);
  }

  public void putCachedJar(final VirtualFile sourceFile, final JarInfo jarInfo) {
    myCachedJarForDir.put(sourceFile, jarInfo);
  }

  private JarInfo getCachedJar(BuildConfiguration configuration) {
    return myCachedJarForConfiguration.get(configuration);
  }

  private void putCachedJar(BuildConfiguration configuration, JarInfo info) {
    myCachedJarForConfiguration.put(configuration, info);
  }

  public ProcessingItemsBuilder.NestedJarInfo createNestedJarInfo(final DestinationInfo destinationInfo, final BuildConfiguration buildConfiguration,
                                                                  BuildRecipe buildRecipe) {
    JarInfo jarInfo = getCachedJar(buildConfiguration);
    boolean addJarContent = jarInfo == null;
    if (jarInfo == null) {
      List<String> classpath = DeploymentUtilImpl.getExternalDependenciesClasspath(buildRecipe);
      jarInfo = new JarInfo(classpath);
      putCachedJar(buildConfiguration, jarInfo);
    }
    jarInfo.addDestination(destinationInfo);
    return new ProcessingItemsBuilder.NestedJarInfo(jarInfo, destinationInfo, addJarContent);
  }

  public void registerDestination(final BuildParticipant buildParticipant, final ExplodedDestinationInfo destinationInfo) {
    myDestinationOwners.put(destinationInfo, buildParticipant);
  }

  public Map<ExplodedDestinationInfo, BuildParticipant> getDestinationOwners() {
    return myDestinationOwners;
  }

  public CompileContext getCompileContext() {
    return myCompileContext;
  }

  public void addManifestFile(final ManifestFileInfo manifestFileInfo) {
    myManifestFiles.add(manifestFileInfo);
  }
}
