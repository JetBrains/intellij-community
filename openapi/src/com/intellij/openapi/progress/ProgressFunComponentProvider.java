/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.progress;

import com.intellij.openapi.project.Project;

import javax.swing.*;

public interface ProgressFunComponentProvider {
  JComponent getProgressFunComponent(Project project, String processId);
}