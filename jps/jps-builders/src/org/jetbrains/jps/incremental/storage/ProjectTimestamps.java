package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/7/11
 */
public class ProjectTimestamps {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.ProjectTimestamps");
  private static final String TIMESTAMP_STORAGE = "timestamps";
  private final TimestampStorage myTimestamps;
  private final File myTimestampsRoot;

  public ProjectTimestamps(final File dataStorageRoot) throws IOException {
    myTimestampsRoot = new File(dataStorageRoot, TIMESTAMP_STORAGE);
    myTimestamps = new TimestampStorage(new File(myTimestampsRoot, "data"));
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
      FileUtil.delete(myTimestampsRoot);
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
        FileUtil.delete(myTimestampsRoot);
      }
    }
  }
}
