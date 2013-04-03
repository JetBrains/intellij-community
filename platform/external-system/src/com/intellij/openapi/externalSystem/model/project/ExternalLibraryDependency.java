package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/10/11 6:46 PM
 */
public class ExternalLibraryDependency extends AbstractExternalDependency<ExternalLibrary> implements Named {

  public ExternalLibraryDependency(@NotNull ExternalModule ownerModule, @NotNull ExternalLibrary library) {
    super(ownerModule, library);
  }

  @Override
  public void invite(@NotNull ExternalEntityVisitor visitor) {
    visitor.visit(this); 
  }

  @NotNull
  @Override
  public ExternalLibraryDependency clone(@NotNull ExternalEntityCloneContext context) {
    ExternalLibraryDependency result = new ExternalLibraryDependency(getOwnerModule().clone(context), getTarget().clone(context));
    copyTo(result);
    return result;
  }
}
