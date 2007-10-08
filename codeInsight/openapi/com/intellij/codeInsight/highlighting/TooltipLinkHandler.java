/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public abstract class TooltipLinkHandler {
  public abstract void handleLink(@NotNull String descriptionSuffix, @NotNull Editor editor, @NotNull JEditorPane tooltipComponent);
  @Nullable
  public String getDescription(String descriptionSuffix) {
    return null;
  }
}
