/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.classFilesIndex.indexer.api;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public abstract class ClassFileIndexer<K, V> {
  private final String myIndexCanonicalName;

  public ClassFileIndexer(final String indexCanonicalName) {
    myIndexCanonicalName = indexCanonicalName;
  }

  @NotNull
  public abstract Map<K, V> map(ClassReader inputData, Mappings mappings);

  public abstract KeyDescriptor<K> getKeyDescriptor();

  public abstract DataExternalizer<V> getDataExternalizer();

  public String getIndexCanonicalName() {
    return myIndexCanonicalName;
  }
}
