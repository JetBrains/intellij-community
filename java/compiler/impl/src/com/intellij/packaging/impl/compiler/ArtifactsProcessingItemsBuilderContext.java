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
package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.packagingCompiler.DestinationInfo;
import com.intellij.compiler.impl.packagingCompiler.ExplodedDestinationInfo;
import com.intellij.compiler.impl.packagingCompiler.JarInfo;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.ArtifactIncrementalCompilerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ArtifactsProcessingItemsBuilderContext implements ArtifactIncrementalCompilerContext {
  private boolean myCollectingEnabledItems;
  protected final Map<VirtualFile, ArtifactPackagingProcessingItem> myItemsBySource;
  private final Map<String, VirtualFile> mySourceByOutput;
  private final Map<String, JarInfo> myJarByPath;
  private final CompileContext myCompileContext;

  public ArtifactsProcessingItemsBuilderContext(CompileContext compileContext) {
    myCompileContext = compileContext;
    myItemsBySource = new HashMap<VirtualFile, ArtifactPackagingProcessingItem>();
    mySourceByOutput = new HashMap<String, VirtualFile>();
    myJarByPath = new HashMap<String, JarInfo>();
  }

  public boolean addDestination(@NotNull VirtualFile sourceFile, @NotNull DestinationInfo destinationInfo) {
    if (destinationInfo instanceof ExplodedDestinationInfo && sourceFile.equals(destinationInfo.getOutputFile())) {
      return false;
    }

    if (checkOutputPath(destinationInfo.getOutputPath(), sourceFile)) {
      getOrCreateProcessingItem(sourceFile).addDestination(destinationInfo, myCollectingEnabledItems);
      return true;
    }
    return false;
  }

  public ArtifactPackagingProcessingItem[] getProcessingItems() {
    final Collection<ArtifactPackagingProcessingItem> processingItems = myItemsBySource.values();
    return processingItems.toArray(new ArtifactPackagingProcessingItem[processingItems.size()]);
  }

  public void setCollectingEnabledItems(boolean collectingEnabledItems) {
    myCollectingEnabledItems = collectingEnabledItems;
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

  public ArtifactPackagingProcessingItem getItemBySource(VirtualFile source) {
    return myItemsBySource.get(source);
  }

  public boolean registerJarFile(@NotNull JarInfo jarInfo, @NotNull String outputPath) {
    if (myJarByPath.containsKey(outputPath)) {
      return false;
    }
    myJarByPath.put(outputPath, jarInfo);
    return true;
  }

  @Nullable
  public JarInfo getJarInfo(String outputPath) {
    return myJarByPath.get(outputPath);
  }

  @Nullable
  public VirtualFile getSourceByOutput(String outputPath) {
    return mySourceByOutput.get(outputPath);
  }

  public CompileContext getCompileContext() {
    return myCompileContext;
  }

  public ArtifactPackagingProcessingItem getOrCreateProcessingItem(VirtualFile sourceFile) {
    ArtifactPackagingProcessingItem item = myItemsBySource.get(sourceFile);
    if (item == null) {
      item = new ArtifactPackagingProcessingItem(sourceFile);
      myItemsBySource.put(sourceFile, item);
    }
    return item;
  }
}
