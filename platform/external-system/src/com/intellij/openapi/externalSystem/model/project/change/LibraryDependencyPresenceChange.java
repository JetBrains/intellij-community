package com.intellij.openapi.externalSystem.model.project.change;

import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.id.EntityIdMapper;
import com.intellij.openapi.externalSystem.model.project.id.LibraryDependencyId;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.roots.LibraryOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/24/12 9:48 AM
 */
public class LibraryDependencyPresenceChange extends AbstractProjectEntityPresenceChange<LibraryDependencyId> {

  public LibraryDependencyPresenceChange(@Nullable LibraryDependencyData gradleDependency,
                                         @Nullable LibraryOrderEntry intellijDependency) throws IllegalArgumentException
  {
    super(ExternalSystemBundle.message("entity.type.library.dependency"), of(gradleDependency), of(intellijDependency));
  }

  @Override
  public void invite(@NotNull ExternalProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }

  @Nullable
  private static LibraryDependencyId of(@Nullable Object dependency) {
    if (dependency == null) {
      return null;
    }
    return EntityIdMapper.mapEntityToId(dependency);
  }
}
