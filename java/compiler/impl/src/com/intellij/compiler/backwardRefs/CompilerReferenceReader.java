// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.CompilerRef;
import org.jetbrains.jps.backwardRefs.NameEnumerator;
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public abstract class CompilerReferenceReader<Index extends CompilerReferenceIndex<?>> {
  protected final Index myIndex;
  private final Path buildDir;

  public CompilerReferenceReader(@NotNull Path buildDir, Index index) {
    myIndex = index;
    this.buildDir = buildDir;
  }

  /**
   * @deprecated Use {@link #CompilerReferenceReader(Path, CompilerReferenceIndex)}
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  public CompilerReferenceReader(@NotNull File buildDir, Index index) {
    myIndex = index;
    this.buildDir = buildDir.toPath();
  }

  public @NotNull NameEnumerator getNameEnumerator() {
    return myIndex.getByteSeqEum();
  }

  public void close(boolean removeIndex) {
    myIndex.close();
    if (removeIndex) {
      CompilerReferenceIndex.removeIndexFiles(buildDir);
    }
  }

  public Index getIndex() {
    return myIndex;
  }

  public abstract @Nullable Set<VirtualFile> findReferentFileIds(@NotNull CompilerRef ref, boolean checkBaseClassAmbiguity) throws StorageException;

  public abstract @Nullable Set<VirtualFile> findFileIdsWithImplicitToString(@NotNull CompilerRef ref) throws StorageException;

  public abstract @Nullable Map<VirtualFile, SearchId[]> getDirectInheritors(@NotNull CompilerRef searchElement,
                                                                             @NotNull GlobalSearchScope searchScope,
                                                                             @NotNull GlobalSearchScope dirtyScope,
                                                                             @NotNull FileType fileType,
                                                                             @NotNull CompilerHierarchySearchType searchType) throws StorageException;

  public abstract @Nullable Integer getAnonymousCount(@NotNull CompilerRef.CompilerClassHierarchyElementDef classDef, boolean checkDefinitions);

  public abstract int getOccurrenceCount(@NotNull CompilerRef element);

  public abstract CompilerRef.CompilerClassHierarchyElementDef @Nullable("return null if the class hierarchy contains ambiguous qualified names") [] getHierarchy(CompilerRef.CompilerClassHierarchyElementDef hierarchyElement,
                                                                                                                                                                  boolean checkBaseClassAmbiguity,
                                                                                                                                                                  boolean includeAnonymous,
                                                                                                                                                                  int interruptNumber);
  public @NotNull SearchId @Nullable[] getDirectInheritorsNames(CompilerRef hierarchyElement) throws StorageException {
    return null;
  }
}
