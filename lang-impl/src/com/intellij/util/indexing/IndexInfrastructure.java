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
  private static final int VERSION = 4;
  private final static TObjectLongHashMap<ID<?, ?>> myIndexIdToCreationStamp = new TObjectLongHashMap<ID<?, ?>>();
  private static final boolean ourUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();

  private IndexInfrastructure() {
  }

  public static File getVersionFile(final ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName + ".ver");
  }

  public static File getStorageFile(final ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName.toString());
  }

  public static File getIndexRootDir(final ID<?, ?> indexName) {
    final String dirName = indexName.toString().toLowerCase(Locale.US);
    // store StubIndices under StubUpdating index' root to ensure they are deleted 
    // when StubUpdatingIndex version is changed 
    final File indexDir = (indexName instanceof StubIndexKey)? 
                          new File(getIndexRootDir(StubUpdatingIndex.INDEX_ID), dirName) : 
                          new File(getPersistenceRoot(), dirName);
    indexDir.mkdirs();
    return indexDir;
  }

  public static File getPersistenceRoot() {
    File file = new File(PathManager.getSystemPath(), "index");
    try {
      file = file.getCanonicalFile();
    }
    catch (IOException ignored) {
    }
    file.mkdirs();
    return file;
  }

  public static void rewriteVersion(final File file, final int version) throws IOException {
    if (file.exists()) {
      FileUtil.delete(file);
      try {
        // need this to ensure the timestamp of the newly created file will be different
        Thread.sleep(501);
      }
      catch (InterruptedException ignored) {
      }
    }
    file.getParentFile().mkdirs();
    file.createNewFile();
    final DataOutputStream os = new DataOutputStream(new FileOutputStream(file));
    try {
      os.writeInt(version);
      os.writeInt(VERSION);
    }
    finally {
      myIndexIdToCreationStamp.clear();
      os.close();
    }
  }

  public static long getIndexCreationStamp(ID<?, ?> indexName) {
    long stamp = myIndexIdToCreationStamp.get(indexName);
    if (stamp <= 0) {
      stamp = getVersionFile(indexName).lastModified();
      myIndexIdToCreationStamp.put(indexName, stamp);
    }
    return stamp;
  }

  public static boolean versionDiffers(final File versionFile, final int currentIndexVersion) {
    try {
      final DataInputStream in = new DataInputStream(new FileInputStream(versionFile));
      try {
        final int savedIndexVersion = in.readInt();
        final int commonVersion = in.readInt();
        return (savedIndexVersion != currentIndexVersion) || (commonVersion != VERSION);
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
  private static VirtualFile findTestFile(final int id) {
    return ourUnitTestMode ? DummyFileSystem.getInstance().findById(id) : null;
  }
}