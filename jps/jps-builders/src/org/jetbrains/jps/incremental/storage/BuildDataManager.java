package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.incremental.Builder;
import org.jetbrains.jps.incremental.BuilderCategory;
import org.jetbrains.jps.incremental.BuilderRegistry;
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
  private static final String OUTPUTS_STORAGE = "out-src";
  private final String myProjectName;

  private final Map<String, TimestampStorage> myBuilderToStampStorageMap = new HashMap<String, TimestampStorage>();
  private final OutputToSourceMapping myOutputToSourceMap;

  public BuildDataManager(String projectName)  {
    myProjectName = projectName;
    myOutputToSourceMap = createOutputToSourceMap();
  }

  private OutputToSourceMapping createOutputToSourceMap() {
    final File root = getOutputToSourceStorageRoot();
    final File dataFile = new File(root, "data");
    try {
      return new OutputToSourceMapping(dataFile);
    }
    catch (Exception e) {
      FileUtil.delete(root);
      try {
        return new OutputToSourceMapping(dataFile);
      }
      catch (Exception e1) {
        throw new RuntimeException(e1);
      }
    }
  }

  public TimestampStorage getTimestampStorage(String builderName) throws Exception {
    synchronized (myBuilderToStampStorageMap) {
      TimestampStorage storage = myBuilderToStampStorageMap.get(builderName);
      if (storage == null) {
        storage = new TimestampStorage(new File(getTimestampsStorageRoot(builderName), "data"));
        myBuilderToStampStorageMap.put(builderName, storage);
      }
      return storage;
    }
  }

  public OutputToSourceMapping getOutputToSourceStorage() {
    return myOutputToSourceMap;
  }

  public void clean() {
    synchronized (myBuilderToStampStorageMap) {
      try {
        final BuilderRegistry registry = BuilderRegistry.getInstance();
        for (BuilderCategory category : BuilderCategory.values()) {
          for (Builder builder : registry.getBuilders(category)) {
            cleanTimestampStorage(builder.getName());
          }
        }
      }
      finally {
        myOutputToSourceMap.wipe();
      }
    }
  }

  private void cleanTimestampStorage(String builderName) {
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

  public void close() {
    try {
      closeTimestampStorages();
    }
    finally {
      synchronized (myOutputToSourceMap) {
        try {
          myOutputToSourceMap.close();
        }
        catch (IOException e) {
          LOG.error(e);
          FileUtil.delete(getOutputToSourceStorageRoot());
        }
      }
    }
  }

  private void closeTimestampStorages() {
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

  public File getOutputToSourceStorageRoot() {
    return new File(Paths.getDataStorageRoot(myProjectName), OUTPUTS_STORAGE);
  }

  public File getTimestampsStorageRoot(String builderName) {
    return new File(Paths.getBuilderDataRoot(myProjectName, builderName), TIMESTAMP_STORAGE);
  }
}
