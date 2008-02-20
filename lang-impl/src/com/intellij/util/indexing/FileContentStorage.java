package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.SLRUCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 19, 2008
 */
public class FileContentStorage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.FileContentStorage");
  private int myKeyBeingRemoved = -1;
  private final File myStorageRoot;
  private static final byte[] EMPTY_BYTE_ARRAY = new byte[]{};
  
  private SLRUCache<Integer, byte[]> myCache = new SLRUCache<Integer, byte[]>(128, 32) {
    @NotNull
    public byte[] createValue(final Integer key) {
      final File dataFile = getDataFile(key);
      try {
        return FileUtil.loadFileBytes(dataFile);
      }
      catch (IOException ignored) {
      }
      return EMPTY_BYTE_ARRAY;
    }

    protected void onDropFromCache(final Integer key, final byte[] bytes) {
      if (key.intValue() == myKeyBeingRemoved) {
        FileUtil.delete(getDataFile(key));
      }
      else {
        final File dataFile = getDataFile(key);
        try {
          final BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(dataFile));
          try {
            os.write(bytes);
          }
          finally {
            os.close();
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  };


  private File getDataFile(final int fileId) {
    return new File(myStorageRoot, String.valueOf(fileId));
  }

  public FileContentStorage(File storageRoot) {
    myStorageRoot = storageRoot;
    storageRoot.mkdirs();
  }

  public void offer(VirtualFile file) {
    try {
      final byte[] bytes = file.contentsToByteArray();
      if (bytes != null && bytes.length > 0) {
        myCache.put(FileBasedIndex.getFileId(file), bytes);
      }
    }
    catch (FileNotFoundException ignored) {
      // may happen, if content was never queried before
      // In this case the index for this file must not have been built and it is ok to ignore the file
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Nullable
  public byte[] remove(VirtualFile file) {
    final int fileId = FileBasedIndex.getFileId(file);
    try {
      final byte[] bytes = myCache.get(fileId);
      return bytes.length == 0 ? null : bytes;
    }
    finally {
      myKeyBeingRemoved = fileId;
      myCache.remove(fileId);
      myKeyBeingRemoved = -1;
    }
  }
  
  
}
