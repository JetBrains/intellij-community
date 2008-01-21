package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;


public class LibraryModuleGroupUrl extends AbstractUrl {

  @NonNls private static final String ELEMENT_TYPE = "libraryModuleGroup";

  public LibraryModuleGroupUrl(String moduleName) {
    super(null, moduleName, ELEMENT_TYPE);
  }

  public Object[] createPath(Project project) {
    final Module module = moduleName != null ? ModuleManager.getInstance(project).findModuleByName(moduleName) : null;
    if (module == null) return null;
    return new Object[]{new LibraryGroupElement(module)};
  }

  protected AbstractUrl createUrl(String moduleName, String url) {
      return new LibraryModuleGroupUrl(moduleName);
  }

  public AbstractUrl createUrlByElement(Object element) {
    if (element instanceof LibraryGroupElement) {
      LibraryGroupElement libraryGroupElement = (LibraryGroupElement)element;
      return new LibraryModuleGroupUrl(libraryGroupElement.getModule() != null ? libraryGroupElement.getModule().getName() : null);
    }
    return null;
  }
}
