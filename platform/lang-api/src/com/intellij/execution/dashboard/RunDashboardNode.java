// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard;

import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.Nullable;

/**
 * @author konstantin.aleev
 */
public interface RunDashboardNode {
  default @Nullable RunContentDescriptor getDescriptor() {
    return null;
  }

  default @Nullable Content getContent() {
    return null;
  }

  Project getProject();

  default @Nullable Runnable getRemover() {
    return null;
  }
}
