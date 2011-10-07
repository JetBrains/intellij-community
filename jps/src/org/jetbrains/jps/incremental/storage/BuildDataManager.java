package org.jetbrains.jps.incremental.storage;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/7/11
 */
public class BuildDataManager {
  private final File myDataRoot;
  //private final PersistentHashMap<File, Long> myTimestamps = new PersistentHashMap<File, Long>();
  public BuildDataManager(File dataRoot) {
    myDataRoot = dataRoot;
  }


}
