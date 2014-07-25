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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.classFilesIndex.indexer.api.storage.ClassFilesIndexStorageBase;
import org.jetbrains.jps.classFilesIndex.indexer.api.storage.ClassFilesIndexStorageWriter;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class ClassFilesIndexWriter<K, V> {
  private static final Logger LOG = Logger.getInstance(ClassFilesIndexWriter.class);

  private final ClassFileIndexer<K, V> myIndexer;
  private final boolean myEmpty;
  private final Mappings myMappings;
  private final ClassFilesIndexStorageWriter<K, V> myIndex;

  protected ClassFilesIndexWriter(final ClassFileIndexer<K, V> indexer, final CompileContext compileContext) {
    myIndexer = indexer;
    final File storageDir = getIndexRoot(compileContext);
    final Set<String> containingFileNames = listFiles(storageDir);
    if (!containingFileNames.contains("version") || !containingFileNames.contains(IndexState.STATE_FILE_NAME)) {
      throw new IllegalStateException("version or state file for index " + indexer.getIndexCanonicalName() + " not found in " + storageDir.getAbsolutePath());
    }
    ClassFilesIndexStorageWriter<K, V> index = null;
    IOException exception = null;
    LOG.debug("start open... " + indexer.getIndexCanonicalName());
    myMappings = compileContext.getProjectDescriptor().dataManager.getMappings();
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        index = new ClassFilesIndexStorageWriter<K, V>(storageDir,
                                                       myIndexer.getKeyDescriptor(),
                                                       myIndexer.getDataExternalizer(),
                                                       myMappings);
        break;
      }
      catch (final IOException e) {
        exception = e;
        PersistentHashMap.deleteFilesStartingWith(ClassFilesIndexStorageBase.getIndexFile(storageDir));
      }
    }
    LOG.debug("opened " + indexer.getIndexCanonicalName());
    if (index == null) {
      throw new RuntimeException(exception);
    }
    myIndex = index;
    myEmpty = IndexState.EXIST != IndexState.load(storageDir) || exception != null;
    IndexState.CORRUPTED.save(storageDir);
  }

  private static Set<String> listFiles(final File dir) {
    final String[] containingFileNames = dir.list();
    return containingFileNames == null ? Collections.<String>emptySet() : ContainerUtil.newHashSet(containingFileNames);
  }

  private File getIndexRoot(final CompileContext compileContext) {
    final File rootFile = compileContext.getProjectDescriptor().dataManager.getDataPaths().getDataStorageRoot();
    return ClassFilesIndexStorageBase.getIndexDir(myIndexer.getIndexCanonicalName(), rootFile);
  }

  public final boolean isEmpty() {
    return myEmpty;
  }

  public final void close(final CompileContext compileContext) {
    try {
      myIndex.close();
      IndexState.EXIST.save(getIndexRoot(compileContext));
    }
    catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public final void update(final String id, final ClassReader inputData) {
    for (final Map.Entry<K, V> e : myIndexer.map(inputData, myMappings).entrySet()) {
      myIndex.putData(e.getKey(), e.getValue(), id);
    }
  }
}
