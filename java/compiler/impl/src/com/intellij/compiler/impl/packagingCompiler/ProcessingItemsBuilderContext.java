/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
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
  private final Map<VirtualFile, JarInfo> myCachedJarForDir;
  private final MultiValuesMap<String, JarInfo> myJarsByPath;
  private final CompileContext myCompileContext;
  private final List<ManifestFileInfo> myManifestFiles;

  public ProcessingItemsBuilderContext(final CompileContext compileContext) {
    myCompileContext = compileContext;
    myManifestFiles = new ArrayList<ManifestFileInfo>();
    myItemsBySource = new HashMap<VirtualFile, Item>();
    mySourceByOutput = new HashMap<String, VirtualFile>();
    myCachedJarForDir = new HashMap<VirtualFile, JarInfo>();
    myJarsByPath = new MultiValuesMap<String, JarInfo>();
  }

  public List<ManifestFileInfo> getManifestFiles() {
    return myManifestFiles;
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

  public JarInfo getCachedJar(final VirtualFile sourceFile) {
    return myCachedJarForDir.get(sourceFile);
  }

  public void putCachedJar(final VirtualFile sourceFile, final JarInfo jarInfo) {
    myCachedJarForDir.put(sourceFile, jarInfo);
  }

  public CompileContext getCompileContext() {
    return myCompileContext;
  }

  public void addManifestFile(final ManifestFileInfo manifestFileInfo) {
    myManifestFiles.add(manifestFileInfo);
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
