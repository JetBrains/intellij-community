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
import com.intellij.openapi.compiler.FileProcessingCompiler;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public abstract class ProcessingItemsBuilderContext<Item extends FileProcessingCompiler.ProcessingItem> {
  protected final Map<VirtualFile, Item> myItemsBySource;
  private final Map<String, VirtualFile> mySourceByOutput;
  private final MultiValuesMap<String, JarInfo> myJarsByPath;
  private final CompileContext myCompileContext;

  public ProcessingItemsBuilderContext(final CompileContext compileContext) {
    myCompileContext = compileContext;
    myItemsBySource = new HashMap<VirtualFile, Item>();
    mySourceByOutput = new HashMap<String, VirtualFile>();
    myJarsByPath = new MultiValuesMap<String, JarInfo>();
  }

  public abstract Item[] getProcessingItems();

  public boolean checkOutputPath(final String outputPath, final VirtualFile sourceFile) {
    VirtualFile old = mySourceByOutput.get(outputPath);
    if (old == null) {
      mySourceByOutput.put(outputPath, sourceFile);
      return true;
    }
    //todo[nik] show warning?
    return false;
  }

  public Item getItemBySource(VirtualFile source) {
    return myItemsBySource.get(source);
  }

  public void registerJarFile(@NotNull JarInfo jarInfo, @NotNull String outputPath) {
    myJarsByPath.put(outputPath, jarInfo);
  }

  @Nullable
  public Collection<JarInfo> getJarInfos(String outputPath) {
    return myJarsByPath.get(outputPath);
  }

  @Nullable
  public VirtualFile getSourceByOutput(String outputPath) {
    return mySourceByOutput.get(outputPath);
  }

  public CompileContext getCompileContext() {
    return myCompileContext;
  }

  public Item getOrCreateProcessingItem(VirtualFile sourceFile) {
    Item item = myItemsBySource.get(sourceFile);
    if (item == null) {
      item = createProcessingItem(sourceFile);
      myItemsBySource.put(sourceFile, item);
    }
    return item;
  }

  protected abstract Item createProcessingItem(VirtualFile sourceFile);
}
