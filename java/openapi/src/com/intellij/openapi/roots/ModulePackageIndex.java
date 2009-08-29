package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;

public abstract class ModulePackageIndex extends PackageIndex {
  public static ModulePackageIndex getInstance(Module module) {
    return ModuleServiceManager.getService(module, ModulePackageIndex.class);
  }
}
