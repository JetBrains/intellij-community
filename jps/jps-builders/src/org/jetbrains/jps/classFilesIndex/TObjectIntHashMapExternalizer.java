/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.classFilesIndex;

import com.intellij.util.io.DataExternalizer;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Dmitry Batkovich
 */
public class TObjectIntHashMapExternalizer<K> implements DataExternalizer<TObjectIntHashMap<K>> {
  private final DataExternalizer<K> myKeyDataExternalizer;

  public TObjectIntHashMapExternalizer(final DataExternalizer<K> keyDataExternalizer) {
    myKeyDataExternalizer = keyDataExternalizer;
  }

  @Override
  public void save(@NotNull final DataOutput out, final TObjectIntHashMap<K> map) throws IOException {
    out.writeInt(map.size());
    try {
      map.forEachEntry(new TObjectIntProcedure<K>() {
        @Override
        public boolean execute(final K key, final int value) {
          try {
            myKeyDataExternalizer.save(out, key);
            out.writeInt(value);
          }
          catch (final IOException e) {
            throw new IoExceptionRuntimeWrapperException(e);
          }
          return true;
        }
      });
    }
    catch (final IoExceptionRuntimeWrapperException e) {
      throw e.getIoException();
    }
  }

  @Override
  public TObjectIntHashMap<K> read(@NotNull final DataInput in) throws IOException {
    final int size = in.readInt();
    final TObjectIntHashMap<K> map = new TObjectIntHashMap<K>(size);
    for (int i = 0; i < size; i++) {
      map.put(myKeyDataExternalizer.read(in), in.readInt());
    }
    return map;
  }

  private static class IoExceptionRuntimeWrapperException extends RuntimeException {
    private final IOException myIoException;

    private IoExceptionRuntimeWrapperException(final IOException ioException) {
      myIoException = ioException;
    }

    public IOException getIoException() {
      return myIoException;
    }
  }
}
