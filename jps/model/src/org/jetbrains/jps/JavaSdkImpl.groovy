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

  @Override
  public String getHomePath() {
    return jdkPath;
  }

  @Override
  public String getVersion() {
    return version;
  }

  public String getJavacExecutable() {
    return jdkPath + File.separator + "bin" + File.separator + "javac";
  }

  public String getJavaExecutable() {
    return jdkPath + File.separator + "bin" + File.separator + "java";
  }
}
