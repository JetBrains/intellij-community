package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.SLRUCache;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 19, 2008
 */
public class FileContentStorage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.FileContentStorage");
  private static final byte[] EMPTY_BYTE_ARRAY = new byte[]{};

  private int myKeyBeingRemoved = -1;
  private final File myStorageRoot;
  private final TIntHashSet myFileIds = new TIntHashSet();
  private final Object myLock = new Object();

  private SLRUCache<Integer, byte[]> myCache = new SLRUCache<Integer, byte[]>(200, 56) {
    @NotNull
    public byte[] createValue(final Integer key) {
      final int _keyValue = key.intValue();
      if (myFileIds.contains(_keyValue)) {
        final File dataFile = getDataFile(_keyValue);
        try {
          return FileUtil.loadFileBytes(dataFile);
        }
        catch (IOException ignored) {
        }
      }
      return EMPTY_BYTE_ARRAY;
    }

    protected void onDropFromCache(final Integer key, final byte[] bytes) {
      if (key.intValue() == myKeyBeingRemoved) {
        FileUtil.delete(getDataFile(key));
      }
      else if (bytes.length > 0){
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
    final boolean wasCreated = storageRoot.mkdirs();
    if (!wasCreated) {
      final String[] names = storageRoot.list();
      if (names != null) {
        for (String name : names) {
          try {
            myFileIds.add(Integer.parseInt(name));
          }
          catch (NumberFormatException ignored) {
          }
        }
      }
    }
  }

  public void offer(VirtualFile file) {
    try {
      final byte[] bytes = file.contentsToByteArray();
      if (bytes != null) {
        synchronized (myLock) {
          final int fileId = FileBasedIndex.getFileId(file);
          myFileIds.add(fileId);
          myCache.put(fileId, bytes);
        }
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
  public synchronized byte[] remove(VirtualFile file) {
    final int fileId = FileBasedIndex.getFileId(file);
    synchronized (myLock) {
      try {
        return myCache.get(fileId);
      }
      finally {
        myKeyBeingRemoved = fileId;
        myCache.remove(fileId);
        myFileIds.remove(fileId);
        myKeyBeingRemoved = -1;
      }
    }
  }
  
  
}
