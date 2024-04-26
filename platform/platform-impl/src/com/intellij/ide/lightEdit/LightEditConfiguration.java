// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.wm.impl.FrameInfo;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class LightEditConfiguration {
  enum PreferredMode {
    LightEdit,
    Project
  }

  public boolean      autosaveMode = false;
  public List<String> sessionFiles = new ArrayList<>();

  public List<String> supportedFilePatterns = null;

  public @Nullable FrameInfo frameInfo;

  public PreferredMode preferredMode;
}
