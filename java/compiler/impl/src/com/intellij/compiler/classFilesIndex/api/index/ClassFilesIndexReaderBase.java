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
package com.intellij.compiler.classFilesIndex.api.index;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.classFilesIndex.indexer.api.IndexState;
import org.jetbrains.jps.classFilesIndex.indexer.api.storage.ClassFilesIndexStorageBase;
import org.jetbrains.jps.incremental.storage.BuildDataManager;

import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry Batkovich
 */
public abstract class ClassFilesIndexReaderBase<K, V> {

  public static final String VERSION_FILE_NAME  = "version";

  private final static Logger LOG = Logger.getInstance(ClassFilesIndexReaderBase.class);
  @Nullable
  protected final ClassFilesIndexStorageReader<K, V> myIndex;
  @Nullable
  protected final Mappings myMappings;

  public static boolean checkIndexAndRecreateIfNeed(final Project project, final int currentVersion, final String canonicalIndexName) {
    final File projectBuildSystemDirectory = BuildManager.getInstance().getProjectSystemDirectory(project);
    assert projectBuildSystemDirectory != null;
    final File versionFile = new File(ClassFilesIndexStorageBase.getIndexDir(canonicalIndexName, projectBuildSystemDirectory), VERSION_FILE_NAME);
    final File indexDir = ClassFilesIndexStorageBase.getIndexDir(canonicalIndexName, projectBuildSystemDirectory);
    if (versionFile.exists() &&
        !versionDiffers(projectBuildSystemDirectory, canonicalIndexName, currentVersion) &&
        IndexState.load(indexDir) == IndexState.EXIST) {
      return true;
    }
    else {
      recreateIndex(canonicalIndexName, currentVersion, projectBuildSystemDirectory, indexDir);
      return false;
    }
  }

  @SuppressWarnings("ConstantConditions")
  protected ClassFilesIndexReaderBase(final KeyDescriptor<K> keyDescriptor,
                                      final DataExternalizer<V> valueExternalizer,
                                      final String canonicalIndexName,
                                      final int indexVersion,
                                      final Project project) {
    if (checkIndexAndRecreateIfNeed(project, indexVersion, canonicalIndexName)) {
      ClassFilesIndexStorageReader<K, V> index = null;
      IOException exception = null;
      final File projectBuildSystemDirectory = BuildManager.getInstance().getProjectSystemDirectory(project);
      final File indexDir = ClassFilesIndexStorageBase.getIndexDir(canonicalIndexName, projectBuildSystemDirectory);
      try {
        index = new ClassFilesIndexStorageReader<>(indexDir, keyDescriptor, valueExternalizer);
      }
      catch (final IOException e) {
        exception = e;
        PersistentHashMap.deleteFilesStartingWith(ClassFilesIndexStorageBase.getIndexFile(indexDir));
      }
      if (exception != null) {
        recreateIndex(canonicalIndexName, indexVersion, projectBuildSystemDirectory, indexDir);
        myIndex = null;
        myMappings = null;
      }
      else {
        myIndex = index;
        try {
          myMappings = new Mappings(BuildDataManager.getMappingsRoot(projectBuildSystemDirectory),false);
        }
        catch (final IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
    else {
      myIndex = null;
      myMappings = null;
    }
  }

  private static void recreateIndex(final String canonicalIndexName,
                                    final int indexVersion,
                                    final File projectBuildSystemDirectory,
                                    final File indexDir) {
    if (indexDir.exists()) {
      FileUtil.delete(indexDir);
    }
    try {
      FileUtil.writeToFile(new File(ClassFilesIndexStorageBase.getIndexDir(canonicalIndexName, projectBuildSystemDirectory), VERSION_FILE_NAME),
                           String.valueOf(indexVersion));
    }
    catch (final IOException e) {
      throw new RuntimeException(e);
    }
    IndexState.NOT_EXIST.save(indexDir);
  }

  public boolean isEmpty() {
    return myIndex == null;
  }

  public final void close() {
    if (myIndex != null) {
      try {
        myIndex.close();
      }
      catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public final void delete() {
    try {
      if (myIndex != null) {
        myIndex.delete();
      }
    }
    catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static File getVersionFile(final File projectBuildSystemDirectory, final String canonicalIndexName) {
    return new File(ClassFilesIndexStorageBase.getIndexDir(canonicalIndexName, projectBuildSystemDirectory), VERSION_FILE_NAME);
  }

  private static boolean versionDiffers(final File projectBuildSystemDirectory, final String canonicalIndexName, final int currentVersion) {
    final File versionFile = getVersionFile(projectBuildSystemDirectory, canonicalIndexName);
    if (!versionFile.exists()) {
      return true;
    }
    try {
      return Integer.parseInt(FileUtil.loadFile(versionFile)) != currentVersion;
    }
    catch (final IOException e) {
      LOG.error("error while reading version file " + versionFile.getAbsolutePath());
      return true;
    }
  }
}
