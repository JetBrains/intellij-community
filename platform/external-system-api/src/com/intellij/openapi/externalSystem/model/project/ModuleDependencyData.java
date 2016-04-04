package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:40 PM
 */
public class ModuleDependencyData extends AbstractDependencyData<ModuleData> {

  private static final long serialVersionUID = 1L;
  private boolean myProductionOnTestDependency;
  private Collection<String> myModuleDependencyArtifacts;

  public ModuleDependencyData(@NotNull ModuleData ownerModule, @NotNull ModuleData module) {
    super(ownerModule, module);
  }

  public boolean isProductionOnTestDependency() {
    return myProductionOnTestDependency;
  }

  public void setProductionOnTestDependency(boolean productionOnTestDependency) {
    myProductionOnTestDependency = productionOnTestDependency;
  }

  public Collection<String> getModuleDependencyArtifacts() {
    return myModuleDependencyArtifacts;
  }

  public void setModuleDependencyArtifacts(Collection<String> moduleDependencyArtifacts) {
    myModuleDependencyArtifacts = moduleDependencyArtifacts;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    ModuleDependencyData that = (ModuleDependencyData)o;
    if (myProductionOnTestDependency != that.myProductionOnTestDependency) return false;
    if (myModuleDependencyArtifacts != null ? !myModuleDependencyArtifacts.equals(that.myModuleDependencyArtifacts)
                                            : that.myModuleDependencyArtifacts != null) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myProductionOnTestDependency ? 1 : 0);
    result = 31 * result + (myModuleDependencyArtifacts != null ? myModuleDependencyArtifacts.hashCode() : 0);
    return result;
  }
}
