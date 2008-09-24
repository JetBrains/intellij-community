package com.intellij.compiler;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 17, 2008
 */
public interface CompilerSettingsFactory {
  ExtensionPointName<CompilerSettingsFactory> EP_NAME = ExtensionPointName.create("com.intellij.compilerSettingsFactory");

  Configurable create(Project project);
}
