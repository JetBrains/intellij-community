// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

public interface AlignedPopup {

  void showUnderneathOf(@NotNull Component aComponent, boolean useAlignment);

  static void showUnderneathWithoutAlignment(@NotNull JBPopup popup, @NotNull Component parent) {
    if (popup instanceof AlignedPopup ap) {
      ap.showUnderneathOf(parent, false);
    }
    else {
      popup.showUnderneathOf(parent);
    }
  }
}
