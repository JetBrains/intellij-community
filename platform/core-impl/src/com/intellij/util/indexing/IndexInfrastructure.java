/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.stubs.StubUpdatingIndex;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Locale;

@SuppressWarnings({"HardCodedStringLiteral"})
public class IndexInfrastructure {
  private static final int VERSION = 9;
  private static final TObjectLongHashMap<ID<?, ?>> ourIndexIdToCreationStamp = new TObjectLongHashMap<ID<?, ?>>();

  private IndexInfrastructure() {
  }

  public static File getVersionFile(final ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName + ".ver");
  }

  public static File getStorageFile(final ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName.toString());
  }

  public static File getInputIndexStorageFile(final ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName.toString()+"_inputs");
  }

  public static File getIndexRootDir(final ID<?, ?> indexName) {
    final String dirName = indexName.toString().toLowerCase(Locale.US);
    // store StubIndices under StubUpdating index' root to ensure they are deleted 
    // when StubUpdatingIndex version is changed 
    final File indexDir = indexName instanceof StubIndexKey ?
                          new File(getIndexRootDir(StubUpdatingIndex.INDEX_ID), dirName) : 
                          new File(PathManager.getIndexRoot(), dirName);
    indexDir.mkdirs();
    return indexDir;
  }

  public static void rewriteVersion(final File file, final int version) throws IOException {
    final long prevLastModifiedValue = file.lastModified();
    if (file.exists()) {
      FileUtil.delete(file);
    }
    file.getParentFile().mkdirs();
    final DataOutputStream os = new DataOutputStream(new FileOutputStream(file));
    try {
      os.writeInt(version);
      os.writeInt(VERSION);
    }
    finally {
      synchronized (ourIndexIdToCreationStamp) {
        ourIndexIdToCreationStamp.clear();
      }
      os.close();
      file.setLastModified(Math.max(System.currentTimeMillis(), prevLastModifiedValue + 1000));
    }
  }

  public static long getIndexCreationStamp(ID<?, ?> indexName) {
    synchronized (ourIndexIdToCreationStamp) {
      long stamp = ourIndexIdToCreationStamp.get(indexName);
      if (stamp <= 0) {
        stamp = getVersionFile(indexName).lastModified();
        ourIndexIdToCreationStamp.put(indexName, stamp);
      }
      return stamp;
    }
  }

  public static boolean versionDiffers(final File versionFile, final int currentIndexVersion) {
    try {
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


}
