package org.jetbrains.jps.javac;

import javax.tools.*;
import java.util.Collection;

/**
* @author Eugene Zhuravlev
*         Date: 1/22/12
*/
public interface DiagnosticOutputConsumer extends DiagnosticListener<JavaFileObject> {
  void outputLineAvailable(String line);
  void registerImports(String className, Collection<String> imports, Collection<String> staticImports);
}
