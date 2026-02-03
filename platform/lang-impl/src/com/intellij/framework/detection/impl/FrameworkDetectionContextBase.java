// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl;

import com.intellij.framework.detection.FrameworkDetectionContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public abstract class FrameworkDetectionContextBase implements FrameworkDetectionContext {
  @Override
  public @Nullable Project getProject() {
    return null;
  }
}
