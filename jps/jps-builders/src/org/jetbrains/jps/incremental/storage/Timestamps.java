package org.jetbrains.jps.incremental.storage;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/24/12
 */
public interface Timestamps {
  void force();

  void saveStamp(File file, long timestamp) throws IOException;

  void removeStamp(File file) throws IOException;

  long getStamp(File file) throws IOException;
}
