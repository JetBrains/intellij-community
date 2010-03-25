package com.intellij.execution.runners;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * For now, used only to patch the debugged run configuration. May be extended to other executors when needed.
 *
 * @author peter
 */
public interface JavaProgramPatcher {
  ExtensionPointName<JavaProgramPatcher> EP_NAME = ExtensionPointName.create("com.intellij.java.programPatcher");

  void patchJavaParameters(Executor executor, RunProfile configuration, JavaParameters javaParameters);
}
