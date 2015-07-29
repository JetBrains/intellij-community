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
package com.intellij.compiler.classFilesIndex.impl;

import com.intellij.compiler.classFilesIndex.api.index.ClassFilesIndexConfigure;
import com.intellij.compiler.classFilesIndex.api.index.ClassFilesIndexReaderBase;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.PersistentHashMapValueStorage;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.jps.classFilesIndex.indexer.api.ClassFileIndexerFactory;
import org.jetbrains.jps.classFilesIndex.indexer.impl.EnumeratedMethodIncompleteSignature;
import org.jetbrains.jps.classFilesIndex.indexer.impl.MethodsUsageIndexerFactory;
import org.jetbrains.jps.classFilesIndex.indexer.impl.MethodsUsageIndexer;

/**
 * @author Dmitry Batkovich
 */
public class MethodsUsageIndexConfigure extends ClassFilesIndexConfigure<Integer, TObjectIntHashMap<EnumeratedMethodIncompleteSignature>> {

  public static final MethodsUsageIndexConfigure INSTANCE = new MethodsUsageIndexConfigure();

  @Override
  public String getIndexCanonicalName() {
    return MethodsUsageIndexer.METHODS_USAGE_INDEX_CANONICAL_NAME;
  }

  @Override
  public int getIndexVersion() {
    return 1 + (PersistentHashMapValueStorage.COMPRESSION_ENABLED ? 0xFF : 0);
  }

  @Override
  public Class<? extends ClassFileIndexerFactory> getIndexerBuilderClass() {
    return MethodsUsageIndexerFactory.class;
  }

  @Override
  public ClassFilesIndexReaderBase<Integer, TObjectIntHashMap<EnumeratedMethodIncompleteSignature>> createIndexReader(final Project project) {
    return new MethodsUsageIndexReader(project, getIndexCanonicalName(), getIndexVersion());
  }
}
