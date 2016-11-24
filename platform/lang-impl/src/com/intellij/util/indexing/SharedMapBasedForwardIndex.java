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
import com.intellij.util.indexing.impl.AbstractForwardIndex;
import com.intellij.util.indexing.impl.CollectionInputKeyIterator;
import com.intellij.util.indexing.impl.DebugAssertions;
import com.intellij.util.indexing.impl.MapBasedForwardIndex;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

class SharedMapBasedForwardIndex<Key, Value> extends AbstractForwardIndex<Key,Value> {
  private final DataExternalizer<Collection<Key>> mySnapshotIndexExternalizer;
  private MapBasedForwardIndex<Key, Value> myUnderlying;

  public SharedMapBasedForwardIndex(MapBasedForwardIndex<Key, Value> underlying) {
    super(underlying.getIndexExtension());
    myUnderlying = underlying;
    mySnapshotIndexExternalizer = VfsAwareMapReduceIndex.createInputsIndexExternalizer(underlying.getIndexExtension());
  }

  @NotNull
  @Override
  public InputKeyIterator<Key, Value> getInputKeys(int inputId) throws IOException {
    Collection<Key> keys;
    if (SharedIndicesData.ourFileSharedIndicesEnabled) {
      keys = SharedIndicesData.recallFileData(inputId, myIndexId, mySnapshotIndexExternalizer);
      Collection<Key> keysFromInputsIndex = myUnderlying.getInputsIndex().get(inputId);

      if ((keys == null && keysFromInputsIndex != null) ||
          !DebugAssertions.equals(keysFromInputsIndex, keys, myKeyDescriptor)
        ) {
        SharedIndicesData.associateFileData(inputId, myIndexId, keysFromInputsIndex, mySnapshotIndexExternalizer);
        if (keys != null) {
          DebugAssertions.error(
            "Unexpected indexing diff " + myIndexId + ", file:" + IndexInfrastructure.findFileById(PersistentFS.getInstance(), inputId)
            + "," + keysFromInputsIndex + "," + keys);
        }
        keys = keysFromInputsIndex;
      }
      return new CollectionInputKeyIterator<>(keys);
    }
    return new CollectionInputKeyIterator<>(myUnderlying.getInputsIndex().get(inputId));
  }

  @Override
  public void putInputData(int inputId, @NotNull Map<Key, Value> data)
    throws IOException {
    Collection<Key> keySeq = data.keySet();
    myUnderlying.putData(inputId, keySeq);
    if (SharedIndicesData.ourFileSharedIndicesEnabled) {
      if (keySeq.size() == 0) keySeq = null;
      SharedIndicesData.associateFileData(inputId, myIndexId, keySeq, mySnapshotIndexExternalizer);
    }
  }

  @Override
  public void flush() {
    myUnderlying.flush();
  }

  @Override
  public void clear() throws IOException {
    myUnderlying.clear();
  }

  @Override
  public void close() throws IOException {
    myUnderlying.close();
  }
}
