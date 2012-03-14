package org.jetbrains.jps

import org.jetbrains.annotations.Nullable

/**
 * @author nik
 */
class JavaSdkImpl extends JavaSdk {
  String jdkPath
  @Nullable String version

  def JavaSdkImpl(Project project, String name, String version, String jdkPath, Closure initializer) {
    super(project, name, initializer)
    this.version = version
    this.jdkPath = jdkPath
  }

  String getJavacExecutable() {
    return jdkPath + File.separator + "bin" + File.separator + "javac";
  }

  String getJavaExecutable() {
    return jdkPath + File.separator + "bin" + File.separator + "java";
  }
}
