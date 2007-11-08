package com.intellij.openapi.module;

/**
 * @author yole
 */
public class ModuleServiceManager {
  public static <T> T getService(Module module, Class<T> serviceClass) {
    return (T)module.getPicoContainer().getComponentInstance(serviceClass.getName());
  }
}