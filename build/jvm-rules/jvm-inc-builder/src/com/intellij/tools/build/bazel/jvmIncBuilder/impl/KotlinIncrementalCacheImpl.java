// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.StorageManager;
import com.intellij.tools.build.bazel.jvmIncBuilder.ZipOutputBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.DependencyGraph;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.java.JVMClassNode;
import org.jetbrains.jps.dependency.java.KotlinMeta;
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCache;
import org.jetbrains.kotlin.load.kotlin.incremental.components.JvmPackagePartProto;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.jetbrains.jps.javac.Iterators.*;

public final class KotlinIncrementalCacheImpl implements IncrementalCache {
  private static final String KOTLIN_MODULE_EXTENSION = ".kotlin_module";
  private final @Nullable String myModuleEntryPath;

  private byte @Nullable [] myLastGoodModuleEntryContent;
  private final Collection<String> myObsoletePackageParts;

  public KotlinIncrementalCacheImpl(StorageManager storageManager, Iterable<NodeSource> outdatedSources) throws IOException {
    myObsoletePackageParts = computeObsoletePackageParts(storageManager.getGraph(), outdatedSources);
    ZipOutputBuilder outBuilder = storageManager.getOutputBuilder();
    myModuleEntryPath = find(outBuilder.listEntries("META-INF/"), n -> n.endsWith(KOTLIN_MODULE_EXTENSION));
    if (myModuleEntryPath != null) {
      myLastGoodModuleEntryContent = outBuilder.getContent(myModuleEntryPath);
    }
  }

  /**
   *  Returns names of obsolete package parts that should be removed from .kotlin_module.
   *  The logic of original function was as follows:
   *  1. Collect all deleted and modified source files (dirty+removed)
   *  2. Get output files generated from these sources
   *  3. Extract package/className from outputs
   *  4. Filter names to only include file facades (like AppKt) or multifile parts (available in both bytecode and metadata)
   *  e.g. kotlin.Metadata.Kind == FILE_FACADE or MULTIFILE_CLASS_PART
   *  5. Return JVM names that will be cleaned from .kotlin_module
   */
  private static @NotNull Collection<String> computeObsoletePackageParts(DependencyGraph depGraph, Iterable<NodeSource> outdatedSources) {
    Set<String> result = new HashSet<>();
    for (Node<?, ?> node : filter(flat(map(outdatedSources, depGraph::getNodes)), n -> n instanceof JVMClassNode)) {
      JVMClassNode<?, ?> clsNode = (JVMClassNode<?, ?>)node;
      KotlinMeta meta = (KotlinMeta)find(clsNode.getMetadata(), mt -> mt instanceof KotlinMeta);
      if (meta != null && meta.isTopLevelDeclarationContainer()) {
        result.add(clsNode.getName());
      }
    }
    return result;
  }

  @Override
  public @NotNull Collection<String> getObsoletePackageParts() {
    return myObsoletePackageParts;
  }

  public @Nullable String getModuleEntryPath() {
    return myModuleEntryPath;
  }

  /**
   *  This function should return .kotlin_module file content as byte array from the previous compilation
   *  or null if it is rebuild or first compilation
   *  Expected to return the state of the last successful compilation
   */
  @Override
  public @Nullable byte[] getModuleMappingData() {
    return myLastGoodModuleEntryContent;
  }

  // No need to implement functions below because they are not used and will be removed in the future
  @Override
  public @NotNull Collection<String> getObsoleteMultifileClasses() {
    throw new UnsupportedOperationException("getObsoleteMultifileClasses() call is not implemented");
  }

  @Override
  public @Nullable Collection<String> getStableMultifileFacadeParts(@NotNull String s) {
    throw new UnsupportedOperationException("getStableMultifileFacadeParts() call is not implemented");
  }

  @Override
  public @Nullable JvmPackagePartProto getPackagePartData(@NotNull String s) {
    throw new UnsupportedOperationException("getPackagePartData() call is not implemented");
  }

  @Override
  public @NotNull String getClassFilePath(@NotNull String s) {
    return "";
  }

  @Override
  public void close() {
    // empty
  }
}
