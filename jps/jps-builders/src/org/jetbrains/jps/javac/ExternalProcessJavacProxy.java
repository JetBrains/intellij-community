package org.jetbrains.jps.javac;

import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.api.RequestFuture;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/22/12
 */
public class ExternalProcessJavacProxy extends JavacProxy{

  private final CanceledStatus myCanceledStatus;
  private final JavacServerClient myClient;

  public ExternalProcessJavacProxy(CanceledStatus canceledStatus, JavacServerClient client) {
    myCanceledStatus = canceledStatus;
    myClient = client;
  }

  public boolean compile(List<String> options, Collection<File> files, Collection<File> classpath, Collection<File> platformCp, Collection<File> sourcePath, Map<File, Set<File>> outs, DiagnosticOutputConsumer diagnosticSink, OutputFileConsumer outputSink) {
    final RequestFuture<JavacServerResponseHandler> future = myClient.sendCompileRequest(options, files, classpath, platformCp, sourcePath, outs, diagnosticSink, outputSink);
    try {
      future.get();
    }
    catch (InterruptedException e) {
      e.printStackTrace(System.err);
    }
    catch (ExecutionException e) {
      e.printStackTrace(System.err);
    }
    return future.getResponseHandler().isTerminatedSuccessfully();
  }
}
