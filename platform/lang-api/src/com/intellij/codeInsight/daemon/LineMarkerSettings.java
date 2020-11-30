// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class LineMarkerSettings {
  public static LineMarkerSettings getSettings() {
    return ApplicationManager.getApplication().getService(LineMarkerSettings.class);
  }

  public abstract boolean isEnabled(@NotNull GutterIconDescriptor descriptor);

  public abstract void setEnabled(@NotNull GutterIconDescriptor descriptor, boolean selected);
}
