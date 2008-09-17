package com.intellij.compiler;

import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 17, 2008
 */
public interface BackendCompilerFactory {
  ExtensionPointName<BackendCompilerFactory> EP_NAME = ExtensionPointName.create("com.intellij.backendCompilerFactory");

  BackendCompiler create(Project project);
}
