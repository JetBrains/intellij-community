/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.filters;

import com.intellij.openapi.project.Project;

public interface HyperlinkInfo {
  void navigate(Project project);
}
