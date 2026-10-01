// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkersAccessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class LineMarkersAccessorImpl implements LineMarkersAccessor {
  @Override
  public @NotNull List<LineMarkerInfo<?>> getDisplayedLineMarkers(@NotNull Document document, @NotNull Project project) {
    return LineMarkersPass.getDisplayedLineMarkers(document, project);
  }
}
