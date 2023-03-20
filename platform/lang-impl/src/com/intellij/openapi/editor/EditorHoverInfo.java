// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

@Internal
public final class EditorHoverInfo {
  private final @Nullable HighlightHoverInfo highlightHoverInfo;
  private final @Nullable DocumentationHoverInfo documentationHoverInfo;

  EditorHoverInfo(@Nullable HighlightHoverInfo highlightHoverInfo, @Nullable DocumentationHoverInfo documentationHoverInfo) {
    assert highlightHoverInfo != null || documentationHoverInfo != null;
    this.highlightHoverInfo = highlightHoverInfo;
    this.documentationHoverInfo = documentationHoverInfo;
  }

  public @Nullable JComponent createComponent(@NotNull Editor editor, @NotNull PopupBridge popupBridge, boolean requestFocus) {
    Project project = Objects.requireNonNull(editor.getProject());
    boolean quickDocShownInPopup = documentationHoverInfo != null && documentationHoverInfo.showInPopup(project);
    JComponent c1 = highlightHoverInfo == null
                    ? null
                    : highlightHoverInfo.createHighlightInfoComponent(editor, !quickDocShownInPopup, popupBridge, requestFocus);
    JComponent c2 = documentationHoverInfo == null
                    ? null
                    : documentationHoverInfo.createQuickDocComponent(editor, c1 != null, popupBridge);
    assert quickDocShownInPopup == (c2 != null);
    if (c1 == null && c2 == null) return null;
    JPanel p = new JPanel(new CombinedPopupLayout(c1, c2));
    p.setBorder(null);
    if (c1 != null) p.add(c1);
    if (c2 != null) p.add(c2);
    return p;
  }
}
