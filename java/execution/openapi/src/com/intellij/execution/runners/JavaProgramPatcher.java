package com.intellij.execution.runners;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * Patch Java command line before running/debugging
 *
 * @author peter
 */
public abstract class JavaProgramPatcher {
  public static final ExtensionPointName<JavaProgramPatcher> EP_NAME = ExtensionPointName.create("com.intellij.java.programPatcher");

  public abstract void patchJavaParameters(Executor executor, RunProfile configuration, JavaParameters javaParameters);
}
