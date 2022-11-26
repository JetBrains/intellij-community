// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TooltipLinkHandler {

  /**
   * Override to handle mouse clicks on a link.
   *
   * @param refSuffix part of link's href attribute after registered prefix.
   * @param editor    an editor in which tooltip with a link was shown.
   * @return {@code true} if a link was handled.
   */
  public boolean handleLink(@NotNull String refSuffix, @NotNull Editor editor) {
    return false;
  }

  /**
   * Override to show extended description on mouse clicks on a link or expand action.
   * This method is only called if {@link #handleLink(String, Editor)}
   * returned {@code false}.
   *
   * @param refSuffix part of link's href attribute after registered prefix.
   * @param editor    an editor in which tooltip with a link was shown.
   * @return detailed description to show.
   */
  @Nullable
  public @InspectionMessage String getDescription(@NotNull String refSuffix, @NotNull Editor editor) {
    return null;
  }

  /**
   * Override to change the title above shown {@link #getDescription(String, Editor)}
   *
   * @param refSuffix part of link's href attribute after registered prefix.
   * @param editor    an editor in which tooltip with a link was shown.
   * @return title above detailed description in the expanded tooltip
   */
  @NotNull
  @InspectionMessage
  public String getDescriptionTitle(@NotNull String refSuffix, @NotNull Editor editor) {
    return IdeBundle.message("inspection.message.inspection.info");
  }
}
