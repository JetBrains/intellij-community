// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public interface LineMarkersAccessor {
  static LineMarkersAccessor getInstance() {
    return ApplicationManager.getApplication().getService(LineMarkersAccessor.class);
  }

  @NotNull List<LineMarkerInfo<?>> getDisplayedLineMarkers(@NotNull Document document, @NotNull Project project);
}
