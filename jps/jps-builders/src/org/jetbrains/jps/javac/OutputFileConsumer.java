package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;

/**
* @author Eugene Zhuravlev
*         Date: 1/22/12
*/
public interface OutputFileConsumer {
  void save(@NotNull OutputFileObject fileObject);
}
