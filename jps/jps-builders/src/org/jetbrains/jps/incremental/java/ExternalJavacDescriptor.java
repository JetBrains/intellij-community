package org.jetbrains.jps.incremental.java;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.openapi.util.Key;
import org.jetbrains.jps.javac.JavacServerClient;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/24/12
 */
public class ExternalJavacDescriptor {
  public static final Key<ExternalJavacDescriptor> KEY = Key.create("_external_javac_descriptor_");

  public final BaseOSProcessHandler process;
  public final JavacServerClient client;

  public ExternalJavacDescriptor(BaseOSProcessHandler process, JavacServerClient client) {
    this.process = process;
    this.client = client;
  }
}
