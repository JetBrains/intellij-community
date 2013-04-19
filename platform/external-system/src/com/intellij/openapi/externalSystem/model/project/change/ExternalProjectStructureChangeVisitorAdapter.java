package com.intellij.openapi.externalSystem.model.project.change;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 3/2/12 4:21 PM
 */
public abstract class ExternalProjectStructureChangeVisitorAdapter implements ExternalProjectStructureChangeVisitor {
  @Override
  public void visit(@NotNull GradleProjectRenameChange change) {
  }

  @Override
  public void visit(@NotNull LanguageLevelChange change) {
  }

  @Override
  public void visit(@NotNull ModulePresenceChange change) {
  }

  @Override
  public void visit(@NotNull ContentRootPresenceChange change) {
  }

  @Override
  public void visit(@NotNull LibraryDependencyPresenceChange change) {
  }

  @Override
  public void visit(@NotNull JarPresenceChange change) {
  }

  @Override
  public void visit(@NotNull OutdatedLibraryVersionChange change) {
  }

  @Override
  public void visit(@NotNull ModuleDependencyPresenceChange change) {
  }

  @Override
  public void visit(@NotNull DependencyScopeChange change) {
  }

  @Override
  public void visit(@NotNull DependencyExportedChange change) {
  }
}
