package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;

public interface ModulesProvider {
  ModulesProvider EMPTY_MODULES_PROVIDER = new ModulesProvider() {
    public Module[] getModules() {
      return Module.EMPTY_ARRAY;
    }
    public Module getModule(String name) {
      return null;
    }

    public ModuleRootModel getRootModel(Module module) {
      return ModuleRootManager.getInstance(module);
    }
  };
  
  Module[] getModules();

  Module getModule(String name);

  ModuleRootModel getRootModel(Module module);
}
