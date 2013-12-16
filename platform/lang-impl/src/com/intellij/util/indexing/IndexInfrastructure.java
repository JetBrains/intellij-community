/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Locale;

@SuppressWarnings({"HardCodedStringLiteral"})
public class IndexInfrastructure {
  private static final int VERSION = 9;
  private static final ConcurrentHashMap<ID<?, ?>, Long> ourIndexIdToCreationStamp = new ConcurrentHashMap<ID<?, ?>, Long>();
  private static final boolean ourUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
  public static final long INVALID_STAMP = -1L;
  public static final long INVALID_STAMP2 = -2L;
  private static final String STUB_VERSIONS = ".versions";

  private IndexInfrastructure() {
  }

  public static File getVersionFile(@NotNull ID<?, ?> indexName) {
    return new File(getIndexDirectory(indexName, true), indexName + ".ver");
  }

  public static File getStorageFile(@NotNull ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName.toString());
  }

  public static File getInputIndexStorageFile(@NotNull ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName.toString()+"_inputs");
  }

  public static File getIndexRootDir(@NotNull ID<?, ?> indexName) {
    return getIndexDirectory(indexName, false);
  }

  private static File getIndexDirectory(ID<?, ?> indexName, boolean forVersion) {
    final String dirName = indexName.toString().toLowerCase(Locale.US);
    // store StubIndices under StubUpdating index' root to ensure they are deleted
    // when StubUpdatingIndex version is changed
    final File indexDir = indexName instanceof StubIndexKey
               ? new File(getIndexRootDir(StubUpdatingIndex.INDEX_ID), forVersion ? STUB_VERSIONS : dirName)
               : new File(PathManager.getIndexRoot(), dirName);
    indexDir.mkdirs();
    return indexDir;
  }

  private static volatile long ourLastStamp; // ensure any file index stamp increases

  public static synchronized void rewriteVersion(final File file, final int version) throws IOException {
    final long prevLastModifiedValue = file.lastModified();
    if (file.exists()) {
      FileUtil.delete(file);
    }
    file.getParentFile().mkdirs();
    final DataOutputStream os = FileUtilRt.doIOOperation(new FileUtilRt.RepeatableIOOperation<DataOutputStream, FileNotFoundException>() {
      @Nullable
      @Override
      public DataOutputStream execute(boolean lastAttempt) throws FileNotFoundException {
        try {
          return new DataOutputStream(new FileOutputStream(file));
        } catch (FileNotFoundException ex) {
          if (lastAttempt) throw ex;
          return null;
        }
      }
    });
    assert os != null;
    try {
      os.writeInt(version);
      os.writeInt(VERSION);
    }
    finally {
      ourIndexIdToCreationStamp.clear();
      os.close();
      long max = Math.max(System.currentTimeMillis(), Math.max(prevLastModifiedValue, ourLastStamp) + 2000);
      ourLastStamp = max;
      file.setLastModified(max);
    }
  }

  public static long getIndexCreationStamp(ID<?, ?> indexName) {
    Long version = ourIndexIdToCreationStamp.get(indexName);
    if (version != null) return version.longValue();

    long stamp = getVersionFile(indexName).lastModified();
    ourIndexIdToCreationStamp.putIfAbsent(indexName, stamp);

    return stamp;
  }

  public static boolean versionDiffers(final File versionFile, final int currentIndexVersion) {
    try {
      ourLastStamp = Math.max(ourLastStamp, versionFile.lastModified());
      final DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(versionFile)));
      try {
        final int savedIndexVersion = in.readInt();
        final int commonVersion = in.readInt();
        return savedIndexVersion != currentIndexVersion || commonVersion != VERSION;
      }
      finally {
        in.close();
      }
    }
    catch (IOException e) {
      return true;
    }
  }

  @Nullable
  public static VirtualFile findFileById(final PersistentFS fs, final int id) {
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
  public static VirtualFile findFileByIdIfCached(final PersistentFS fs, final int id) {
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
