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
package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.make.BuildParticipant;
import com.intellij.openapi.compiler.make.BuildConfiguration;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.deployment.DeploymentUtilImpl;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.packaging.impl.compiler.ArtifactPackagingProcessingItem;

import java.util.*;

/**
 * @author nik
 */
public class OldProcessingItemsBuilderContext extends ProcessingItemsBuilderContext<PackagingProcessingItem> {
  private final Map<BuildConfiguration, JarInfo> myCachedJarForConfiguration;
  private final Map<ExplodedDestinationInfo, BuildParticipant> myDestinationOwners;
  private final MultiValuesMap<Module, PackagingProcessingItem> myItemsByModule;

  public OldProcessingItemsBuilderContext(CompileContext compileContext) {
    super(compileContext);
    myCachedJarForConfiguration = new HashMap<BuildConfiguration, JarInfo>();
    myDestinationOwners = new HashMap<ExplodedDestinationInfo, BuildParticipant>();
    myItemsByModule = new MultiValuesMap<Module, PackagingProcessingItem>();
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

  public PackagingProcessingItem getOrCreateProcessingItem(final VirtualFile sourceFile, BuildParticipant participant) {
    PackagingProcessingItem item = getOrCreateProcessingItem(sourceFile);
    myItemsByModule.put(participant.getModule(), item);
    return item;
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
    if (destinationInfo instanceof ExplodedDestinationInfo) {
      registerJarFile(jarInfo, destinationInfo.getOutputFilePath());
    }
    return new ProcessingItemsBuilder.NestedJarInfo(jarInfo, destinationInfo, addJarContent);
  }

  public void registerDestination(final BuildParticipant buildParticipant, final ExplodedDestinationInfo destinationInfo) {
    myDestinationOwners.put(destinationInfo, buildParticipant);
  }

  public BuildParticipant getDestinationOwner(ExplodedDestinationInfo destination) {
    return myDestinationOwners.get(destination);
  }

  protected PackagingProcessingItem createProcessingItem(VirtualFile sourceFile) {
    return new PackagingProcessingItem(sourceFile);
  }

  public PackagingProcessingItem[] getProcessingItems() {
    final Collection<PackagingProcessingItem> processingItems = myItemsBySource.values();
    return processingItems.toArray(new PackagingProcessingItem[processingItems.size()]);
  }
}
