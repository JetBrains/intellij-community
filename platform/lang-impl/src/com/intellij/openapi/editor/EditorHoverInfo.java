// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.openapi.editor.ex.TooltipAction;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

@Internal
public final class EditorHoverInfo {

  private final @Nullable HighlightHoverInfo highlightHoverInfo;
  public final @Nullable DocumentationPsiHoverInfo documentationHoverInfo;

  public EditorHoverInfo(
    @Nullable HighlightHoverInfo highlightHoverInfo,
    @Nullable DocumentationPsiHoverInfo documentationHoverInfo
  ) {
    assert highlightHoverInfo != null || documentationHoverInfo != null;
    this.highlightHoverInfo = highlightHoverInfo;
    this.documentationHoverInfo = documentationHoverInfo;
  }

  public JComponent createComponent(Editor editor, PopupBridge popupBridge, boolean requestFocus) {
    boolean quickDocShownInPopup = documentationHoverInfo != null &&
                                   ToolWindowManager.getInstance(Objects.requireNonNull(editor.getProject()))
                                     .getToolWindow(ToolWindowId.DOCUMENTATION) == null;
    JComponent c1 = highlightHoverInfo == null
                    ? null
                    : highlightHoverInfo.createHighlightInfoComponent(editor, !quickDocShownInPopup, popupBridge, requestFocus);
    DocumentationComponent c2 = documentationHoverInfo == null
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

  public @NotNull EditorHoverInfo withQuickDoc(@Nullable DocumentationPsiHoverInfo documentationPsiHoverInfo) {
    return new EditorHoverInfo(highlightHoverInfo, documentationPsiHoverInfo);
  }

  public @NotNull EditorHoverInfo withTooltip(@NotNull TooltipAction tooltipAction) {
    if (highlightHoverInfo == null) {
      return this;
    }
    return new EditorHoverInfo(highlightHoverInfo.override(tooltipAction), documentationHoverInfo);
  }
}
