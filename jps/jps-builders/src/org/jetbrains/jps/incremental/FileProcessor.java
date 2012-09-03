package org.jetbrains.jps.incremental;

import java.io.File;
import java.io.IOException;

/**
* @author Eugene Zhuravlev
*         Date: 9/21/11
*/
public interface FileProcessor {
  /**
   * @return true if processing should continue, false if should stop
   */
  boolean apply(ModuleBuildTarget target, File file, String sourceRoot) throws IOException;
}
