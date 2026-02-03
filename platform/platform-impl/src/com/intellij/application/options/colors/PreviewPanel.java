// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.application.options.colors;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface PreviewPanel {
  void blinkSelectedHighlightType(Object selected);

  void disposeUIResources();

  class Empty implements PreviewPanel{
    @Override
    public JComponent getPanel() {
      return null;
    }

    @Override
    public void updateView() {
    }

    @Override
    public void addListener(final @NotNull ColorAndFontSettingsListener listener) {

    }

    @Override
    public void blinkSelectedHighlightType(final Object selected) {

    }

    @Override
    public void disposeUIResources() {

    }
  }

  JComponent getPanel();

  void updateView();

  void addListener(@NotNull ColorAndFontSettingsListener listener);
}
