// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

final class FileAndLineTextRenderer extends ColoredListCellRenderer<SearchEverywhereItem> {

  @Override
  protected void customizeCellRenderer(
    @NotNull JList<? extends SearchEverywhereItem> list,
    @NotNull SearchEverywhereItem value,
    int index, boolean selected, boolean hasFocus
  ) {
    TextChunk[] text = value.getPresentation().getText();
    // line number / file info
    String fileString = value.getPresentation().getFileString();
    String prevFileString = findPrevFile(list, index);
    SimpleTextAttributes attributes = Objects.equals(fileString, prevFileString)
                                      ? FindPopupPanel.UsageTableCellRenderer.REPEATED_FILE_ATTRIBUTES
                                      : FindPopupPanel.UsageTableCellRenderer.ORDINAL_ATTRIBUTES;
    append(fileString, attributes);
    if (text.length > 0) append(" " + text[0].getText(), FindPopupPanel.UsageTableCellRenderer.ORDINAL_ATTRIBUTES);
    setBorder(null);
  }

  @SuppressWarnings("TypeParameterExtendsFinalClass")
  private static @Nullable String findPrevFile(@NotNull JList<? extends SearchEverywhereItem> list, int index) {
    if (index <= 0) return null;
    Object prev = list.getModel().getElementAt(index - 1);
    //noinspection ConstantConditions,CastCanBeRemovedNarrowingVariableType
    return prev instanceof SearchEverywhereItem ? ((SearchEverywhereItem)prev).getPresentation().getFileString() : null;
  }
}
