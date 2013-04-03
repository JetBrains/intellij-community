package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/12/11 12:49 PM
 */
public interface ExternalEntityVisitor {

  void visit(@NotNull ExternalProject project);
  void visit(@NotNull ExternalModule module);
  void visit(@NotNull ExternalContentRoot contentRoot);
  void visit(@NotNull ExternalLibrary library);
  void visit(@NotNull Jar jar);
  void visit(@NotNull ExternalModuleDependency dependency);
  void visit(@NotNull ExternalLibraryDependency dependency);
  void visit(@NotNull ExternalCompositeLibraryDependency dependency);
}
