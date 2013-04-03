package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/23/11 1:16 PM
 */
public abstract class ExternalEntityVisitorAdapter implements ExternalEntityVisitor {
  
  @Override
  public void visit(@NotNull ExternalProject project) {
  }

  @Override
  public void visit(@NotNull ExternalModule module) {
  }

  @Override
  public void visit(@NotNull ExternalContentRoot contentRoot) {
  }

  @Override
  public void visit(@NotNull ExternalLibrary library) {
  }

  @Override
  public void visit(@NotNull Jar jar) {
  }

  @Override
  public void visit(@NotNull ExternalModuleDependency dependency) {
  }

  @Override
  public void visit(@NotNull ExternalLibraryDependency dependency) {
  }

  @Override
  public void visit(@NotNull ExternalCompositeLibraryDependency dependency) {
  }
}
