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
package org.jetbrains.jps.classFilesIndex.indexer.api.storage;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;

import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry Batkovich
 */
public class ClassFilesIndexStorageWriter<K, V> extends ClassFilesIndexStorageBase<K, V> {
  private final Mappings myMappings;

  public ClassFilesIndexStorageWriter(final File indexDir,
                                      final KeyDescriptor<K> keyDescriptor,
                                      final DataExternalizer<V> valueExternalizer,
                                      final Mappings mappings) throws IOException {
    super(indexDir, keyDescriptor, valueExternalizer);
    myMappings = mappings;
  }

  public void putData(final K key, final V value, final String containingClass) {
    final int id = myMappings.getName(containingClass);
    try {
      myWriteLock.lock();
      final CompiledDataValueContainer<V> container = myCache.get(key);
      container.putValue(id, value);
    }
    finally {
      myWriteLock.unlock();
    }
  }
}
