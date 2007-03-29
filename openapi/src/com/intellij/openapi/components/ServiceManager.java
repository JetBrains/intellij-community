package com.intellij.openapi.components;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;


/**
 * For old-style components, the contract specifies a lifecycle: the component gets created and notified during the project opening process.
 * For services, there's no such contract, so we don't even load the class implementing the service until someone requests it. 
 */
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
