// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Function;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.InvertedIndex;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.indexing.export.ForwardIndexBasedExporter;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.indexing.impl.forward.AbstractForwardIndexAccessor;
import com.intellij.util.io.ByteSequenceDataExternalizer;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

public class StubUpdatingIndexExporter extends ForwardIndexBasedExporter<Integer, SerializedStubTree, FileContent> {
  // serialized stub trees
  private PersistentHashMap<ByteArraySequence, ByteArraySequence> myStorageMap;

  @Override
  public void export(@NotNull File dir,
                     @NotNull InvertedIndex<Integer, SerializedStubTree, FileContent> index,
                     @NotNull Function<FileContent, ByteArraySequence> inputSubstitutor,
                     @NotNull ToIntFunction<FileContent> inputEnumerator,
                     @NotNull Stream<FileContent> inputs) throws IOException {
    super.export(dir, index, inputSubstitutor, inputEnumerator, inputs);

    SerializationManagerImpl serializationManager = (SerializationManagerImpl)SerializationManager.getInstance();
    serializationManager.flushNameStorage();
    copyAllFilesStartingWith(SerializationManagerImpl.NAME_STORAGE_FILE, dir);
  }

  @Override
  protected void putInputData(@NotNull ByteArraySequence substitutedInput,
                              int inputId,
                              @NotNull MapReduceIndex<Integer, SerializedStubTree, FileContent> mrIndex) throws IOException {
    super.putInputData(substitutedInput, inputId, mrIndex);
    try {
      ValueContainer<SerializedStubTree> data = mrIndex.getData(inputId);
      Ref<IOException> exception = Ref.create();
      data.forEach((id, tree) -> {
        try {
          myStorageMap.put(substitutedInput, AbstractForwardIndexAccessor.serializeToByteSeq(tree, StubUpdatingIndex.KEY_EXTERNALIZER ,8));
        }
        catch (IOException e) {
          exception.set(e);
        }
        return false;
      });
      if (!exception.isNull()) {
        throw exception.get();
      }
    }
    catch (StorageException e) {
      throw new IOException();
    }
  }

  @Override
  protected void closeDumpTarget() throws IOException {
    try {
      super.closeDumpTarget();
    } finally {
      myStorageMap.close();
    }
  }

  @Override
  protected void openDumpTarget(@NotNull File dir) throws IOException {
    super.openDumpTarget(dir);
    myStorageMap = new PersistentHashMap<>(new File(dir, "stub_trees"), ByteSequenceDataExternalizer.INSTANCE, ByteSequenceDataExternalizer.INSTANCE);
  }

  private static void copyAllFilesStartingWith(@NotNull File file, @NotNull File destDir) throws IOException {
    final String baseName = file.getName();
    File parentFile = file.getParentFile();
    final File[] files = parentFile != null ? parentFile.listFiles(pathname -> pathname.getName().startsWith(baseName)) : null;
    if (files == null) return;
    for (File f: files) {
      FileUtil.copy(f, new File(destDir, f.getName()));
    }
  }
}
