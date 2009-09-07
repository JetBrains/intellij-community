/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.impl.convert;

import com.intellij.openapi.project.Project;

/**
 * @author nik
 */
@Deprecated
public abstract class ProjectFileVersion {
  public static ProjectFileVersion getInstance(Project project) {
    return project.getComponent(ProjectFileVersion.class);
  }


}
