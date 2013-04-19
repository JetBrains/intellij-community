package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.id.ModuleDependencyId;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:40 PM
 */
public class ModuleDependencyData extends AbstractDependencyData<ModuleData> {

  public static final Comparator<ModuleDependencyData> COMPARATOR = new Comparator<ModuleDependencyData>() {
    @Override
    public int compare(ModuleDependencyData o1, ModuleDependencyData o2) {
      return Named.COMPARATOR.compare(o1.getTarget(), o2.getTarget());
    }
  };

  private static final long serialVersionUID = 1L;

  public ModuleDependencyData(@NotNull ModuleData ownerModule, @NotNull ModuleData module) {
    super(ownerModule, module);
  }

  @NotNull
  @Override
  public ProjectEntityId getId(@Nullable DataNode<?> dataNode) {
    return new ModuleDependencyId(getOwner(), getOwnerModule().getName(), getName());
  }
}
