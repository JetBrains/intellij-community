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

/*
 * @author max
 */
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.stubs.StubUpdatingIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Locale;

@SuppressWarnings({"HardCodedStringLiteral"})
public class IndexInfrastructure {
  private static final boolean ourUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
  private static final String STUB_VERSIONS = ".versions";

  private IndexInfrastructure() {
  }

  @NotNull
  public static File getVersionFile(@NotNull ID<?, ?> indexName) {
    return new File(getIndexDirectory(indexName, true), indexName + ".ver");
  }

  @NotNull
  public static File getStorageFile(@NotNull ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName.toString());
  }

  @NotNull
  public static File getInputIndexStorageFile(@NotNull ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName +"_inputs");
  }

  @NotNull
  public static File getIndexRootDir(@NotNull ID<?, ?> indexName) {
    return getIndexDirectory(indexName, false);
  }

  @NotNull
  private static File getIndexDirectory(@NotNull ID<?, ?> indexName, boolean forVersion) {
    final String dirName = indexName.toString().toLowerCase(Locale.US);
    // store StubIndices under StubUpdating index' root to ensure they are deleted
    // when StubUpdatingIndex version is changed
    final File indexDir = indexName instanceof StubIndexKey
               ? new File(getIndexRootDir(StubUpdatingIndex.INDEX_ID), forVersion ? STUB_VERSIONS : dirName)
               : new File(PathManager.getIndexRoot(), dirName);
    indexDir.mkdirs();
    return indexDir;
  }

  @Nullable
  public static VirtualFile findFileById(@NotNull PersistentFS fs, final int id) {
    if (ourUnitTestMode) {
      final VirtualFile testFile = findTestFile(id);
      if (testFile != null) {
        return testFile;
      }
    }

    return fs.findFileById(id);

    /*

    final boolean isDirectory = fs.isDirectory(id);
    final DirectoryInfo directoryInfo = isDirectory ? dirIndex.getInfoForDirectoryId(id) : dirIndex.getInfoForDirectoryId(fs.getParent(id));
    if (directoryInfo != null && (directoryInfo.contentRoot != null || directoryInfo.sourceRoot != null || directoryInfo.libraryClassRoot != null)) {
      return isDirectory? directoryInfo.directory : directoryInfo.directory.findChild(fs.getName(id));
    }
    return null;
    */
  }

  @Nullable
  public static VirtualFile findFileByIdIfCached(@NotNull PersistentFS fs, final int id) {
    if (ourUnitTestMode) {
      final VirtualFile testFile = findTestFile(id);
      if (testFile != null) {
        return testFile;
      }
    }
    return fs.findFileByIdIfCached(id);
  }

  @Nullable
  private static VirtualFile findTestFile(final int id) {
    return DummyFileSystem.getInstance().findById(id);
  }
}
