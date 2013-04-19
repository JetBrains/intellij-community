package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.id.LibraryDependencyId;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @NotNull
  @Override
  public ProjectEntityId getId(@Nullable DataNode<?> dataNode) {
    return new LibraryDependencyId(getOwner(), getOwnerModule().getName(), getName());
  }
}
