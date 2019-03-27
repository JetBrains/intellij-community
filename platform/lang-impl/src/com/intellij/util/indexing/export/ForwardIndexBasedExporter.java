// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.export;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.Function;
import com.intellij.util.indexing.InvertedIndex;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.io.ByteSequenceDataExternalizer;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

public class ForwardIndexBasedExporter<Key, Value, Input> implements IndexExporter<Key, Value, Input> {
  private PersistentHashMap<ByteArraySequence, ByteArraySequence> myExportMap;

  @Override
  public void export(@NotNull File dir,
                     @NotNull InvertedIndex<Key, Value, Input> index,
                     @NotNull Function<Input, ByteArraySequence> inputSubstitutor,
                     @NotNull ToIntFunction<Input> inputEnumerator,
                     @NotNull Stream<Input> inputs) throws IOException {
    MapReduceIndex<Key, Value, Input> mrIndex = (MapReduceIndex<Key, Value, Input>)index;
    ForwardIndex forwardIndexMap = mrIndex.getForwardIndexMap();
    if (forwardIndexMap == null) {
      throw new IllegalStateException(getClass().getName() + " exporter is not applicable to " + mrIndex);
    }
    openDumpTarget(dir);
    try {
      Iterator<Input> iterator = inputs.iterator();
      while (iterator.hasNext()) {
        Input input = iterator.next();
        int inputId = inputEnumerator.applyAsInt(input);
        ByteArraySequence substitutedInput = inputSubstitutor.fun(input);
        putInputData(substitutedInput, inputId, mrIndex);
      }
    } finally {
      closeDumpTarget();
    }
  }

  protected void putInputData(@NotNull ByteArraySequence substitutedInput,
                              int inputId,
                              @NotNull MapReduceIndex<Key, Value, Input> mrIndex) throws IOException {
    ByteArraySequence inputData = mrIndex.getForwardIndexMap().get(inputId);
    //TODO
    if (inputData == null) {
      inputData = new ByteArraySequence(new byte[0]);
    }
    myExportMap.put(substitutedInput, inputData);
  }

  protected void closeDumpTarget() throws IOException {
    myExportMap.close();
  }

  protected void openDumpTarget(@NotNull File dir) throws IOException {
    myExportMap = new PersistentHashMap<>(new File(dir, "prebuilt_forward"),
                                          ByteSequenceDataExternalizer.INSTANCE,
                                          ByteSequenceDataExternalizer.INSTANCE);
  }
}
