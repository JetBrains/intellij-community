package org.jetbrains.jps

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

  abstract String getJavacExecutable();

  abstract String getJavaExecutable();
}