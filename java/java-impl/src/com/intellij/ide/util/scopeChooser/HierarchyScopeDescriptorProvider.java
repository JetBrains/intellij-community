/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class HierarchyScopeDescriptorProvider implements ScopeDescriptorProvider {
  @NotNull
  public ScopeDescriptor[] getScopeDescriptors(final Project project) {
    return new ScopeDescriptor[]{new ClassHierarchyScopeDescriptor(project)};
  }
}