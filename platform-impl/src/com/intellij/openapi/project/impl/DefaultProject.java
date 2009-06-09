/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project.impl;

/**
 * @author peter
 */
public class DefaultProject extends ProjectImpl {
  protected DefaultProject(ProjectManagerImpl manager, String filePath, boolean isOptimiseTestLoadSpeed,
                           String projectName) {
    super(manager, filePath, true, isOptimiseTestLoadSpeed, projectName);
  }
}
