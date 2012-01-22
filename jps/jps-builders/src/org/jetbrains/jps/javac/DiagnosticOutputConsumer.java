package org.jetbrains.jps.javac;

import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

/**
* @author Eugene Zhuravlev
*         Date: 1/22/12
*/
public interface DiagnosticOutputConsumer extends DiagnosticListener<JavaFileObject> {
  void outputLineAvailable(String line);
}
