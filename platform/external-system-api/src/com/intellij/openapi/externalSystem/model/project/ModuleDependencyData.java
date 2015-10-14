package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:40 PM
 */
public class ModuleDependencyData extends AbstractDependencyData<ModuleData> {

  private static final long serialVersionUID = 1L;
  private boolean myProductionOnTestDependency;

  public ModuleDependencyData(@NotNull ModuleData ownerModule, @NotNull ModuleData module) {
    super(ownerModule, module);
  }

  public boolean isProductionOnTestDependency() {
    return myProductionOnTestDependency;
  }

  public void setProductionOnTestDependency(boolean productionOnTestDependency) {
    myProductionOnTestDependency = productionOnTestDependency;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    ModuleDependencyData that = (ModuleDependencyData)o;
    return myProductionOnTestDependency == that.myProductionOnTestDependency;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + (myProductionOnTestDependency ? 1 : 0);
  }
}
