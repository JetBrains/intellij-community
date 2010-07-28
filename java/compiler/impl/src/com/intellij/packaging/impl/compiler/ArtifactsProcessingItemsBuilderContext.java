/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
  protected final Map<VirtualFile, ArtifactCompilerCompileItem> myItemsBySource;
  private final Map<String, VirtualFile> mySourceByOutput;
  private final Map<String, JarInfo> myJarByPath;
  private final CompileContext myCompileContext;

  public ArtifactsProcessingItemsBuilderContext(CompileContext compileContext) {
    myCompileContext = compileContext;
    myItemsBySource = new HashMap<VirtualFile, ArtifactCompilerCompileItem>();
    mySourceByOutput = new HashMap<String, VirtualFile>();
    myJarByPath = new HashMap<String, JarInfo>();
  }

  public boolean addDestination(@NotNull VirtualFile sourceFile, @NotNull DestinationInfo destinationInfo) {
    if (destinationInfo instanceof ExplodedDestinationInfo && sourceFile.equals(destinationInfo.getOutputFile())) {
      return false;
    }

    if (checkOutputPath(destinationInfo.getOutputPath(), sourceFile)) {
      getOrCreateProcessingItem(sourceFile).addDestination(destinationInfo);
      return true;
    }
    return false;
  }

  public Collection<ArtifactCompilerCompileItem> getProcessingItems() {
    return myItemsBySource.values();
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

  public ArtifactCompilerCompileItem getItemBySource(VirtualFile source) {
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

  public ArtifactCompilerCompileItem getOrCreateProcessingItem(VirtualFile sourceFile) {
    ArtifactCompilerCompileItem item = myItemsBySource.get(sourceFile);
    if (item == null) {
      item = new ArtifactCompilerCompileItem(sourceFile);
      myItemsBySource.put(sourceFile, item);
    }
    return item;
  }
}
