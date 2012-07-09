package org.jetbrains.jps

import org.jetbrains.annotations.Nullable

/**
 * @author Eugene.Kudelevsky
 */
public abstract class JavaSdk extends Sdk {
  JavaSdk(project, name) {
    super(project, name)
  }

  @Nullable
  public String getHomePath() {
    return null;
  }

  @Nullable
  public String getVersion() {
    return null;
  }

  public abstract String getJavacExecutable();

  public abstract String getJavaExecutable();
}