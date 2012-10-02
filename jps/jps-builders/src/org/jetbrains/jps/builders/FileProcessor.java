package org.jetbrains.jps.builders;

import java.io.File;
import java.io.IOException;

/**
* @author Eugene Zhuravlev
*         Date: 9/21/11
*/
public interface FileProcessor<R extends BuildRootDescriptor, T extends BuildTarget<R>> {
  /**
   * @return true if processing should continue, false if should stop
   */
  boolean apply(T target, File file, R root) throws IOException;
}
