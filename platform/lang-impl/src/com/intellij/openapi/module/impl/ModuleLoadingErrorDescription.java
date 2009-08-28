package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.ConfigurationErrorDescription;
import com.intellij.openapi.project.ProjectBundle;

import java.io.File;

/**
 * @author nik
 */
public class ModuleLoadingErrorDescription extends ConfigurationErrorDescription {
  private final ModuleManagerImpl.ModulePath myModulePath;
  private final ModuleManagerImpl myModuleManager;

  private ModuleLoadingErrorDescription(final String description, final ModuleManagerImpl.ModulePath modulePath, ModuleManagerImpl moduleManager,
                                        final String elementName) {
    super(elementName, ProjectBundle.message("element.kind.name.module"), description);
    myModulePath = modulePath;
    myModuleManager = moduleManager;
  }

  public ModuleManagerImpl.ModulePath getModulePath() {
    return myModulePath;
  }

  @Override
  public void removeInvalidElement() {
    myModuleManager.removeFailedModulePath(myModulePath);
  }

  @Override
  public String getRemoveConfirmationMessage() {
    return ProjectBundle.message("module.remove.from.project.confirmation", getElementName());
  }

  public static ModuleLoadingErrorDescription create(final String description, final ModuleManagerImpl.ModulePath modulePath,
                                                     ModuleManagerImpl moduleManager) {
    String path = modulePath.getPath();
    int start = path.lastIndexOf(File.separatorChar)+1;
    int finish = path.lastIndexOf('.');
    if (finish == -1 || finish <= start) {
      finish = path.length();
    }
    final String moduleName = path.substring(start, finish);
    return new ModuleLoadingErrorDescription(description, modulePath, moduleManager, moduleName);
  }
}
