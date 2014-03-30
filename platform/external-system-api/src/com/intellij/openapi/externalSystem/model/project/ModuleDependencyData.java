package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:40 PM
 */
public class ModuleDependencyData extends AbstractDependencyData<ModuleData> {

  private static final long serialVersionUID = 1L;

  public ModuleDependencyData(@NotNull ModuleData ownerModule, @NotNull ModuleData module) {
    super(ownerModule, module);
  }
}
