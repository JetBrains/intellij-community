package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/7/11
 */
public class BuildDataManager {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.BuildDataManager");

  public static final String TIMESTAMP_STORAGE = "timestamps";

  private final File myDataRoot;
  private final Map<String, TimestampStorage> myBuilderToStampStorageMap = new HashMap<String, TimestampStorage>();
  //private final Map<String, AbstractStateStorage> myBuilderToStorageMap = new HashMap<String, AbstractStateStorage>();

  public BuildDataManager(File dataRoot) {
    myDataRoot = dataRoot;
  }

  public TimestampStorage getTimestampStorage(String builderName) throws IOException {
    synchronized (myBuilderToStampStorageMap) {
      TimestampStorage storage = myBuilderToStampStorageMap.get(builderName);
      if (storage == null) {
        storage = new TimestampStorage(getStoreFile(builderName, TIMESTAMP_STORAGE));
        myBuilderToStampStorageMap.put(builderName, storage);
      }
      return storage;
    }
  }

  public void clean() {
    synchronized (myBuilderToStampStorageMap) {
      close();
      FileUtil.delete(myDataRoot);
    }
  }

  public void cleanStorage(String builderName) {
    synchronized (myBuilderToStampStorageMap) {
      final TimestampStorage storage = myBuilderToStampStorageMap.remove(builderName);
      if (storage != null) {
        try {
          storage.close();
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
      FileUtil.delete(getDataRoot(builderName, TIMESTAMP_STORAGE));
    }
  }

  public void close() {
    synchronized (myBuilderToStampStorageMap) {
      try {
        for (Map.Entry<String, TimestampStorage> entry : myBuilderToStampStorageMap.entrySet()) {
          final TimestampStorage storage = entry.getValue();
          try {
            storage.close();
          }
          catch (IOException e) {
            LOG.error(e);
            final String builderName = entry.getKey();
            FileUtil.delete(getDataRoot(builderName, TIMESTAMP_STORAGE));
          }
        }
      }
      finally {
        myBuilderToStampStorageMap.clear();
      }
    }
  }

  private File getStoreFile(String builderName, String storageName) {
    return new File(getDataRoot(builderName, storageName), "data");
  }

  private File getDataRoot(String builderName, String storageName) {
    return new File(myDataRoot, builderName + File.separator + storageName);
  }

  //public <K, V> AbstractStateStorage<K, V> getStorage(String builderName) {
  //
  //}
}
