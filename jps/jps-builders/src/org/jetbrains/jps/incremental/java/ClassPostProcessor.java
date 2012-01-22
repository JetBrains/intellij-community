package org.jetbrains.jps.incremental.java;

import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.javac.OutputFileObject;

/**
* @author Eugene Zhuravlev
*         Date: 1/21/12
*/
public interface ClassPostProcessor {
  void process(CompileContext context, OutputFileObject out);
}
