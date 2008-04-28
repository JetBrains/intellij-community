package com.intellij.openapi.module.impl;

import java.io.File;

/**
 * @author nik
 */
public class ModuleLoadingErrorDescription implements RemoveInvalidElementsDialog.ErrorDescription {
  private final String myDescription;
  private final ModuleManagerImpl.ModulePath myModulePath;

  public ModuleLoadingErrorDescription(final String description, final ModuleManagerImpl.ModulePath modulePath) {
    myDescription = description;
    myModulePath = modulePath;
  }

  public String getDescription() {
    return myDescription;
  }

  public String getElementName() {
    String path = myModulePath.getPath();
    int start = path.lastIndexOf(File.separatorChar)+1;
    int finish = path.lastIndexOf('.');
    if (finish == -1 || finish <= start) {
      finish = path.length();
    }
    return path.substring(start, finish);
  }

  public ModuleManagerImpl.ModulePath getModulePath() {
    return myModulePath;
  }
}
