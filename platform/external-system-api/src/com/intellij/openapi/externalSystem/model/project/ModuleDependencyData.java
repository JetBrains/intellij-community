// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class ModuleDependencyData extends AbstractDependencyData<ModuleData> {
  private boolean productionOnTestDependency;
  private Collection<String> moduleDependencyArtifacts;

  @PropertyMapping({"ownerModule", "target"})
  public ModuleDependencyData(@NotNull ModuleData ownerModule, @NotNull ModuleData module) {
    super(ownerModule, module);
  }

  public boolean isProductionOnTestDependency() {
    return productionOnTestDependency;
  }

  public void setProductionOnTestDependency(boolean productionOnTestDependency) {
    this.productionOnTestDependency = productionOnTestDependency;
  }

  public Collection<String> getModuleDependencyArtifacts() {
    return moduleDependencyArtifacts;
  }

  public void setModuleDependencyArtifacts(Collection<String> moduleDependencyArtifacts) {
    this.moduleDependencyArtifacts = moduleDependencyArtifacts;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    ModuleDependencyData that = (ModuleDependencyData)o;
    if (productionOnTestDependency != that.productionOnTestDependency) return false;
    if (moduleDependencyArtifacts != null ? !moduleDependencyArtifacts.equals(that.moduleDependencyArtifacts)
                                          : that.moduleDependencyArtifacts != null) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (productionOnTestDependency ? 1 : 0);
    result = 31 * result + (moduleDependencyArtifacts != null ? moduleDependencyArtifacts.hashCode() : 0);
    return result;
  }
}
