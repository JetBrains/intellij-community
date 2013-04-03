package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

/**
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/10/11 6:31 PM
 */
public interface ExternalDependency extends ExternalEntity {
  
  boolean isExported();

  @NotNull
  DependencyScope getScope();

  @NotNull
  ExternalModule getOwnerModule();
  
  @NotNull
  ExternalDependency clone(@NotNull ExternalEntityCloneContext context);
}
