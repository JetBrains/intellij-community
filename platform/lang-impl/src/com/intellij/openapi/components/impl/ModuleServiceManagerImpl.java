package com.intellij.openapi.components.impl;

import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;

/**
 * @author yole
 */
public class ModuleServiceManagerImpl extends ServiceManagerImpl {
  private static final ExtensionPointName<ServiceDescriptor> MODULE_SERVICES = new ExtensionPointName<ServiceDescriptor>("com.intellij.moduleService");

  public ModuleServiceManagerImpl(Module module) {
    super(true);
    installEP(MODULE_SERVICES, module);
  }
}