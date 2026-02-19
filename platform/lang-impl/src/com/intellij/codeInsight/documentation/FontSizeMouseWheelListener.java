// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.options.FontSize;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.function.Consumer;

/**
 * @deprecated Unused in v2 implementation.
 */
@Deprecated(forRemoval = true)
final class FontSizeMouseWheelListener implements MouseWheelListener {

  private final @NotNull Consumer<? super @NotNull FontSize> mySizeConsumer;

  FontSizeMouseWheelListener(@NotNull Consumer<? super @NotNull FontSize> sizeConsumer) {
    mySizeConsumer = sizeConsumer;
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    if (!EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled() || !EditorUtil.isChangeFontSize(e)) {
      return;
    }

    int rotation = e.getWheelRotation();
    if (rotation == 0) return;
    int change = Math.abs(rotation);
    boolean increase = rotation <= 0;
    FontSize initial = DocumentationComponent.getQuickDocFontSize();
    FontSize newFontSize = initial;
    for (; change > 0; change--) {
      if (increase) {
        newFontSize = newFontSize.larger();
      }
      else {
        newFontSize = newFontSize.smaller();
      }
    }
    if (newFontSize == initial) {
      return;
    }

    DocumentationComponent.setQuickDocFontSize(newFontSize);
    DocFontSizePopup.update(newFontSize);
    mySizeConsumer.accept(newFontSize);
  }
}
