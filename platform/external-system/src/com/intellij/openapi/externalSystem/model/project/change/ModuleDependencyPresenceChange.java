package com.intellij.openapi.externalSystem.model.project.change;

import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.model.project.id.EntityIdMapper;
import com.intellij.openapi.externalSystem.model.project.id.ModuleDependencyId;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.roots.ModuleOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2/17/12 3:14 PM
 */
public class ModuleDependencyPresenceChange extends AbstractProjectEntityPresenceChange<ModuleDependencyId> {

  public ModuleDependencyPresenceChange(@Nullable ModuleDependencyData gradle, @Nullable ModuleOrderEntry intellij) {
    super(ExternalSystemBundle.message("entity.type.module.dependency"), of(gradle), of(intellij));
  }

  @Override
  public void invite(@NotNull ExternalProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }

  @Nullable
  private static ModuleDependencyId of(@Nullable Object dependency) {
    if (dependency == null) {
      return null;
    }
    return EntityIdMapper.mapEntityToId(dependency);
  }
}
