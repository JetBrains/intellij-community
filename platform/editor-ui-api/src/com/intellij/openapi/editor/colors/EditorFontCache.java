// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public abstract class EditorFontCache {

  public static EditorFontCache getInstance() {
    return ApplicationManager.getApplication().getService(EditorFontCache.class);
  }

  @NotNull
  public abstract Font getFont(@Nullable EditorFontType key);
  public abstract void reset();
}
