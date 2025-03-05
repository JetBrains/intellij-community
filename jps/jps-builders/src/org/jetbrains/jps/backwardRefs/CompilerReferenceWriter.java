// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.backwardRefs;

import com.intellij.util.indexing.InvertedIndex;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

public class CompilerReferenceWriter<Input> {
  protected final CompilerReferenceIndex<Input> myIndex;

  public CompilerReferenceWriter(CompilerReferenceIndex<Input> index) {
    myIndex = index;
  }

  public void writeData(int id, Input d) {
    for (InvertedIndex<?, ?, Input> index : myIndex.getIndices()) {
      index.mapInputAndPrepareUpdate(id, d).update();
    }
  }

  public synchronized int enumeratePath(String file) throws IOException {
    return myIndex.getFilePathEnumerator().enumerate(file);
  }

  public Throwable getRebuildRequestCause() {
    return myIndex.getRebuildRequestCause();
  }

  public void setRebuildCause(Throwable e) {
    myIndex.setRebuildRequestCause(e);
  }
 
  public void processDeletedFiles(Collection<String> files) throws IOException {
    for (String file : files) {
      writeData(enumeratePath(new File(file).getPath()), null);
    }
  }

  @ApiStatus.Internal
  public void processDeleted(@NotNull Collection<Path> files) throws IOException {
    for (Path file : files) {
      writeData(enumeratePath(file.toString()), null);
    }
  }

  protected void force() {
    myIndex.force();
  }

  protected void close() {
    myIndex.close();
  }
}
