package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

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
}
