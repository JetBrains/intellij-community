/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface ScopeDescriptorProvider {
  ExtensionPointName<ScopeDescriptorProvider> EP_NAME = ExtensionPointName.create("com.intellij.scopeDescriptorProvider");

  @NotNull
  ScopeDescriptor[] getScopeDescriptors(Project project);
}