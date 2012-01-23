package org.jetbrains.jps.javac;

import org.jetbrains.jps.api.CanceledStatus;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/22/12
 */
public class ExternalProcessJavacProxy extends JavacProxy{

  private final CanceledStatus myCanceledStatus;

  public ExternalProcessJavacProxy(CanceledStatus canceledStatus) {
    myCanceledStatus = canceledStatus;
  }

  public boolean connect() {
    return false; // todo
  }

  public boolean compile(List<String> options, Collection<File> files, Collection<File> classpath, Collection<File> platformCp, Collection<File> sourcePath, Map<File, Set<File>> outs, DiagnosticOutputConsumer diagnosticSink, OutputFileConsumer outputSink) {
    return false; // todo
  }
}
