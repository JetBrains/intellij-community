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
package com.intellij.compiler.classFilesIndex.api.index;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.jps.classFilesIndex.indexer.api.storage.ClassFilesIndexStorageBase;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * @author Dmitry Batkovich
 */
public class ClassFilesIndexStorageReader<K, V> extends ClassFilesIndexStorageBase<K, V> {
  public ClassFilesIndexStorageReader(final File indexDir,
                                      final KeyDescriptor<K> keyDescriptor,
                                      final DataExternalizer<V> valueExternalizer) throws IOException {
    super(indexDir, keyDescriptor, valueExternalizer);
  }

  public Collection<V> getData(final K key) {
    return myCache.get(key).getValues();
  }
}
