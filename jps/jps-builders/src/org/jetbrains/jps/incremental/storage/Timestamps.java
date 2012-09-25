package org.jetbrains.jps.incremental.storage;

import org.jetbrains.jps.builders.BuildTarget;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/24/12
 */
public interface Timestamps {
  void force();

  void saveStamp(File file, BuildTarget<?> buildTarget, long timestamp) throws IOException;

  void removeStamp(File file, BuildTarget<?> buildTarget) throws IOException;

  void clean() throws IOException;

  long getStamp(File file, BuildTarget<?> target) throws IOException;
}
