/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.indexing;

import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

class SharedMapBasedForwardIndex<Key, Value> extends AbstractForwardIndex<Key,Value> {
  private final DataExternalizer<Collection<Key>> mySnapshotIndexExternalizer;
  private final KeyCollectionBasedForwardIndex<Key, Value> myUnderlying;

  SharedMapBasedForwardIndex(IndexExtension<Key, Value, ?> extension, @Nullable KeyCollectionBasedForwardIndex<Key, Value> underlying) {
    super(extension);
    myUnderlying = underlying;
    mySnapshotIndexExternalizer = VfsAwareMapReduceIndex.createInputsIndexExternalizer(extension);
    assert myUnderlying != null || (SharedIndicesData.ourFileSharedIndicesEnabled && !SharedIndicesData.DO_CHECKS);
  }

  @NotNull
  @Override
  public InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId) throws IOException {
    if (SharedIndicesData.ourFileSharedIndicesEnabled) {
      Collection<Key> keys = SharedIndicesData.recallFileData(inputId, (ID<Key, ?>)myIndexId, mySnapshotIndexExternalizer);
      if (myUnderlying != null) {
        Collection<Key> keysFromInputsIndex = myUnderlying.getInput(inputId);

        if (keys == null && keysFromInputsIndex != null ||
            !DebugAssertions.equals(keysFromInputsIndex, keys, myKeyDescriptor)
          ) {
          SharedIndicesData.associateFileData(inputId, (ID<Key, ?>)myIndexId, keysFromInputsIndex, mySnapshotIndexExternalizer);
          if (keys != null) {
            DebugAssertions.error(
              "Unexpected indexing diff " + myIndexId.getName() + ", file:" + IndexInfrastructure.findFileById(PersistentFS.getInstance(), inputId)
              + "," + keysFromInputsIndex + "," + keys);
          }
          keys = keysFromInputsIndex;
        }
      }
      return new CollectionInputDataDiffBuilder<>(inputId, keys);
    }
    return new CollectionInputDataDiffBuilder<>(inputId, myUnderlying.getInput(inputId));
  }

  @Override
  public void putInputData(int inputId, @NotNull Map<Key, Value> data)
    throws IOException {
    Collection<Key> keySeq = data.keySet();
    if (myUnderlying != null) myUnderlying.putInputData(inputId, data);
    if (SharedIndicesData.ourFileSharedIndicesEnabled) {
      if (keySeq.isEmpty()) keySeq = null;
      SharedIndicesData.associateFileData(inputId, (ID<Key, ?>)myIndexId, keySeq, mySnapshotIndexExternalizer);
    }
  }

  @Override
  public void flush() {
    if (myUnderlying != null) myUnderlying.flush();
  }

  @Override
  public void clear() throws IOException {
    if (myUnderlying != null) myUnderlying.clear();
  }

  @Override
  public void close() throws IOException {
    if (myUnderlying != null) myUnderlying.close();
  }
}
