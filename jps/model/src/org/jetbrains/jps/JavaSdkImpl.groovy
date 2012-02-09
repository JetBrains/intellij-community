package org.jetbrains.jps

/**
 * @author nik
 */
class JavaSdkImpl extends Sdk implements JavaSdk {
  String jdkPath

  def JavaSdkImpl(Project project, String name, String jdkPath, Closure initializer) {
    super(project, name, initializer)
    this.jdkPath = jdkPath
  }

  String getJavacExecutable() {
    return jdkPath + File.separator + "bin" + File.separator + "javac";
  }

  String getJavaExecutable() {
    return jdkPath + File.separator + "bin" + File.separator + "java";
  }
}
