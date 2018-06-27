// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import com.intellij.lang.jvm.JvmElement;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;

public abstract class PwaService extends AbstractProjectComponent {
  public static PwaService getInstance(Project project) {
    return project.getComponent(PwaService.class);
  }

  protected PwaService(Project project) {
    super(project);
  }

  public abstract boolean isBytecodeUsed(JvmElement element);
}
