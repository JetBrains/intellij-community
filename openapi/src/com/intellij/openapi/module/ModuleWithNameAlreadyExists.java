/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.module;

public class ModuleWithNameAlreadyExists extends Exception {
  private String myModuleName;

  public ModuleWithNameAlreadyExists(String moduleName) {
    super("Module \'" + moduleName + "\' already exists in the project.");
    myModuleName = moduleName;
  }

  public String getModuleName() {
    return myModuleName;
  }
}
