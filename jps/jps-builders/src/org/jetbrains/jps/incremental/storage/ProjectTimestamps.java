package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.incremental.Paths;

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

  public ProjectTimestamps(String projectName) throws Exception {
    myProjectName = projectName;
    myTimestamps = new TimestampStorage(new File(getTimestampsRoot(projectName), "data"));
  }

  public TimestampStorage getStorage() {
    return myTimestamps;
  }

  public void clean() throws IOException {
    final TimestampStorage timestamps = myTimestamps;
    if (timestamps != null) {
      timestamps.wipe();
    }
    else {
      FileUtil.delete(getTimestampsRoot(myProjectName));
    }
  }

  public void close() {
    final TimestampStorage timestamps = myTimestamps;
    if (timestamps != null) {
      try {
        timestamps.close();
      }
      catch (IOException e) {
        LOG.error(e);
        FileUtil.delete(getTimestampsRoot(myProjectName));
      }
    }
  }

  public static File getTimestampsRoot(final String projectName) {
    return new File(Paths.getDataStorageRoot(projectName), TIMESTAMP_STORAGE);
  }
}
