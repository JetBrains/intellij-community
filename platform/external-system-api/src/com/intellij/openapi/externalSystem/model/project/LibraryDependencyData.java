package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/10/11 6:46 PM
 */
public class LibraryDependencyData extends AbstractDependencyData<LibraryData> implements Named {

  public LibraryDependencyData(@NotNull ModuleData ownerModule, @NotNull LibraryData library) {
    super(ownerModule, library);
  }
}
