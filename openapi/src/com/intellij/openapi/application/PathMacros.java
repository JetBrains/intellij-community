/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application;

import java.util.Set;

public abstract class PathMacros {

  public static PathMacros getInstance() {
    return ApplicationManager.getApplication().getComponent(PathMacros.class);
  }

  public abstract Set<String> getAllMacroNames();

  public abstract String getValue(String name);

  public abstract void setMacro(String name, String value);

  public abstract void removeMacro(String name);
}
