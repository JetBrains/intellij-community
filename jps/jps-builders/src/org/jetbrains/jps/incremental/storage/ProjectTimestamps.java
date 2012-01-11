package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.incremental.Paths;
import org.jetbrains.jps.incremental.ProjectBuildException;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/7/11
 */
public class ProjectTimestamps {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.ProjectTimestamps");
  private static final String TIMESTAMP_STORAGE = "timestamps";
  private final String myProjectName;

  private final TimestampStorage myTimestamps;

  public ProjectTimestamps(String projectName) throws ProjectBuildException {
    myProjectName = projectName;
    try {
      File root = getTimestampsRoot();
      TimestampStorage result;
      final File dataFile = new File(root, "data");
      try {
        result = new TimestampStorage(dataFile);
      }
      catch (Exception e) {
        FileUtil.delete(root);
        result = new TimestampStorage(dataFile);
      }
      myTimestamps = result;
    }
    catch (Exception e) {
      try {
        clean();
      }
      catch (IOException ignored) {
        LOG.info(ignored);
      }
      throw new ProjectBuildException(e);
    }
  }

  public TimestampStorage getStorage() {
    return myTimestamps;
  }

  public void clean() throws IOException {
    final TimestampStorage timestamps = myTimestamps;
    if (timestamps != null) {
      synchronized (timestamps) {
        timestamps.wipe();
      }
    }
    else {
      FileUtil.delete(getTimestampsRoot());
    }
  }

  public void close() {
    final TimestampStorage timestamps = myTimestamps;
    if (timestamps != null) {
      synchronized (timestamps) {
        try {
          timestamps.close();
        }
        catch (IOException e) {
          LOG.error(e);
          FileUtil.delete(getTimestampsRoot());
        }
      }
    }
  }

  public File getTimestampsRoot() {
    return new File(Paths.getDataStorageRoot(myProjectName), TIMESTAMP_STORAGE);
  }
}
