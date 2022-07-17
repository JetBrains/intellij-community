// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@Internal
public interface DocumentationHoverInfo {

  boolean showInPopup(@NotNull Project project);

  @Nullable JComponent createQuickDocComponent(@NotNull Editor editor, boolean jointPopup, @NotNull PopupBridge bridge);
}
