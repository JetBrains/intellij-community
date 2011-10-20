package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.incremental.Paths;

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
  private static final String TIMESTAMP_STORAGE = "stamps";
  private final String myProjectName;

  private final Map<String, TimestampStorage> myBuilderToStampStorageMap = new HashMap<String, TimestampStorage>();

  public BuildDataManager(String projectName) {
    myProjectName = projectName;
  }

  public TimestampStorage getTimestampStorage(String builderName) throws IOException {
    synchronized (myBuilderToStampStorageMap) {
      TimestampStorage storage = myBuilderToStampStorageMap.get(builderName);
      if (storage == null) {
        storage = new TimestampStorage(new File(getTimestampsStorageRoot(builderName), "data"));
        myBuilderToStampStorageMap.put(builderName, storage);
      }
      return storage;
    }
  }

  public void clean() {
    synchronized (myBuilderToStampStorageMap) {
      close();
      FileUtil.delete(Paths.getDataStorageRoot(myProjectName));
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
      FileUtil.delete(Paths.getBuilderDataRoot(myProjectName, builderName));
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
            FileUtil.delete(getTimestampsStorageRoot(builderName));
          }
        }
      }
      finally {
        myBuilderToStampStorageMap.clear();
      }
    }
  }

  public File getTimestampsStorageRoot(String builderName) {
    return new File(Paths.getBuilderDataRoot(myProjectName, builderName), TIMESTAMP_STORAGE);
  }
}
