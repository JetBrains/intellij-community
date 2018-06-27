// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import com.intellij.util.indexing.InvertedIndex;
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class CompilerReferenceWriter<Input> {
  protected final CompilerReferenceIndex<Input> myIndex;

  public CompilerReferenceWriter(CompilerReferenceIndex<Input> index) {
    myIndex = index;
  }

  public void writeData(int id, Input d) {
    for (InvertedIndex<?, ?, Input> index : myIndex.getIndices()) {
      index.update(id, d).compute();
    }
  }

  public synchronized int enumeratePath(String file) throws IOException {
    return myIndex.getFilePathEnumerator().enumerate(file);
  }
  public synchronized int enumerateName(String name) {
    try {
      return myIndex.getByteSeqEum().enumerate(name);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
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

  protected void close() {
    myIndex.close();
  }
}
