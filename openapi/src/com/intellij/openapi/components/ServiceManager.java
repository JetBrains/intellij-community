package com.intellij.openapi.components;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

@SuppressWarnings({"unchecked"})
public class ServiceManager {
  private ServiceManager() {
  }

  public static <T> T getService(Class<T> serviceClass) {
    return (T)ApplicationManager.getApplication().getPicoContainer().getComponentInstance(serviceClass.getName());
  }

  public static <T> T getService(Project project, Class<T> serviceClass) {
    return (T)project.getPicoContainer().getComponentInstance(serviceClass.getName());
  }

  public static <T> T getService(Module module, Class<T> serviceClass) {
    return (T)module.getPicoContainer().getComponentInstance(serviceClass.getName());
  }
}
