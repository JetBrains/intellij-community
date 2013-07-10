package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/10/11 6:46 PM
 */
public class LibraryDependencyData extends AbstractDependencyData<LibraryData> implements Named {
  
  @NotNull private final LibraryLevel myLevel;
  
  public LibraryDependencyData(@NotNull ModuleData ownerModule, @NotNull LibraryData library, @NotNull LibraryLevel level) {
    super(ownerModule, library);
    myLevel = level;
  }

  @NotNull
  public LibraryLevel getLevel() {
    return myLevel;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myLevel.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    return myLevel.equals(((LibraryDependencyData)o).myLevel);
  }
}
