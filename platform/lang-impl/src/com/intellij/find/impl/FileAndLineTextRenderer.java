// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;
import java.util.function.BiFunction;

final class FileAndLineTextRenderer extends ColoredListCellRenderer<Object> {
  private final BiFunction<? super JList<?>, ? super Integer, UsagePresentation> myPrevUsagePresentationProvider;

  FileAndLineTextRenderer(BiFunction<? super JList<?>, ? super Integer, UsagePresentation> prevUsagePresentationProvider) {
    super();
    myPrevUsagePresentationProvider = prevUsagePresentationProvider;
  }

  @Override
  protected void customizeCellRenderer(
    @NotNull JList<?> list,
    @NotNull Object item,
    int index, boolean selected, boolean hasFocus
  ) {
    if (!(item instanceof UsagePresentation presentation)) return;

    TextChunk[] text = presentation.getText();
    // line number / file info
    String fileString = presentation.getFileString();
    String prevFileString = findPrevFile(list, index);
    SimpleTextAttributes attributes = Objects.equals(fileString, prevFileString)
                                      ? FindPopupPanel.UsageTableCellRenderer.REPEATED_FILE_ATTRIBUTES
                                      : FindPopupPanel.UsageTableCellRenderer.ORDINAL_ATTRIBUTES;
    append(fileString, attributes);
    if (text.length > 0) append(" " + text[0].getText(), FindPopupPanel.UsageTableCellRenderer.ORDINAL_ATTRIBUTES);
    setBorder(null);
  }

  private @Nullable String findPrevFile(@NotNull JList<?> list, int index) {
    UsagePresentation prevPresentation = myPrevUsagePresentationProvider.apply(list, index);
    //noinspection ConstantConditions
    return prevPresentation != null ? prevPresentation.getFileString() : null;
  }
}
