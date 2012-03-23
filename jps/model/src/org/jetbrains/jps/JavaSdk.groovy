package org.jetbrains.jps

import org.jetbrains.annotations.Nullable

/**
 * @author Eugene.Kudelevsky
 */
public abstract class JavaSdk extends Sdk {
  def JavaSdk(Project project, String name, String jdkPath, Closure initializer) {
    super(project, name, initializer)
  }

  JavaSdk(project, name, initializer) {
    super(project, name, initializer)
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