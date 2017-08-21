package com.intellij.util.indexing;

/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.impl.ForwardIndex;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.MapBasedForwardIndex;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class IndexerForwardIndex<K, V> implements ForwardIndex<K, V> {

  private final MapBasedForwardIndex<K, V> myDelegate;
  private final DataExternalizer<Collection<K>> myExternalizer;
  private final ID<K, V> myName;
  private final Set<Integer> myDirtyKeys = ContainerUtil.newConcurrentSet();

  public IndexerForwardIndex(MapBasedForwardIndex<K, V> delegate,
                             DataExternalizer<Collection<K>> externalizer,
                             ID<K, V> name) {
    myDelegate = delegate;
    myExternalizer = externalizer;
    myName = name;
  }

  @NotNull
  @Override
  public InputDataDiffBuilder<K, V> getDiffBuilder(int inputId) throws IOException {
    return myDelegate.getDiffBuilder(inputId);
  }

  @Override
  public void putInputData(int inputId, @NotNull Map<K, V> data) throws IOException {
    myDirtyKeys.add(inputId);
    myDelegate.putInputData(inputId, data);
  }

  @Override
  public void flush() {
    System.out.println("flushing forward index for " + myName);
    try {
      myDelegate.flush();
      CassandraIndexTable.getInstance().bulkInsertForward(myName.toString(), 1, myDirtyKeys.stream().map(fileId -> {
        try {
          Collection<K> keys = myDelegate.getInputsIndex().get(fileId);
          if (keys != null) {
            BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream();
            myExternalizer.save(new DataOutputStream(stream), keys);
            return Pair.create(fileId, ByteBuffer.wrap(stream.getInternalBuffer(), 0, stream.size()));
          } else {
            return null;
          }

        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }).filter(pair -> pair != null));
    } catch (Throwable e) {
      e.printStackTrace();
    }
    myDirtyKeys.clear();
    System.out.println("done");
  }

  @Override
  public void clear() throws IOException {
    throw new IllegalStateException();
  }

  @Override
  public void close() throws IOException {
    myDelegate.close();
  }
}