package com.intellij.execution.configurations;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import org.jetbrains.annotations.NotNull;

public interface JavaClasspathPolicyExtender {
  ExtensionPointName<JavaClasspathPolicyExtender> EP_NAME = ExtensionPointName.create("com.intellij.javaClasspathPolicyExtender");

  @NotNull
  ProjectRootsTraversing.RootTraversePolicy extend(Project project, @NotNull ProjectRootsTraversing.RootTraversePolicy policy);

  @NotNull
  ProjectRootsTraversing.RootTraversePolicy extend(Module module, @NotNull ProjectRootsTraversing.RootTraversePolicy policy);
}
