/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;

import java.util.EventObject;

/**
 *  @author dsl
 */
public abstract class ModuleRootEvent extends EventObject{

  protected ModuleRootEvent(final Project project) {
    super(project);
  }

  public abstract boolean isCausedByFileTypesChange();
}
