// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.wm.impl.FrameInfo;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class LightEditConfiguration {
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
