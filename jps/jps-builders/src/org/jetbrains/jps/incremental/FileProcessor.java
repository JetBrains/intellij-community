package org.jetbrains.jps.incremental;

import org.jetbrains.jps.model.module.JpsModule;

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
  boolean apply(JpsModule module, File file, String sourceRoot) throws IOException;
}
