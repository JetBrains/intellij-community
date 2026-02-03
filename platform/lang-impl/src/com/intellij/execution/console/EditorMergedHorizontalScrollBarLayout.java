// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.util.ui.AbstractLayoutManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class EditorMergedHorizontalScrollBarLayout extends AbstractLayoutManager {
  private final JScrollBar myScrollBar;
  private final EditorEx myFirst;
  private final EditorEx mySecond;
  private final boolean myForceAdditionalColumnsUsage;
  private final int myMinLineCount;

  public EditorMergedHorizontalScrollBarLayout(@NotNull JScrollBar scrollBar, @NotNull EditorEx first, @NotNull EditorEx second,
                                               boolean forceAdditionalColumnsUsage, int minLineCount) {
    myScrollBar = scrollBar;
    myFirst = first;
    mySecond = second;
    myForceAdditionalColumnsUsage = forceAdditionalColumnsUsage;
    myMinLineCount = minLineCount;
  }

  @Override
  public Dimension preferredLayoutSize(final Container parent) {
    return new Dimension(0, 0);
  }

  @Override
  public void layoutContainer(final @NotNull Container parent) {
    final int componentCount = parent.getComponentCount();
    if (componentCount == 0) {
      return;
    }

    final EditorEx history = myFirst;
    final EditorEx input = mySecond;
    if (!input.getComponent().isVisible()) {
      parent.getComponent(0).setBounds(parent.getBounds());
      return;
    }

    final Dimension panelSize = parent.getSize();
    final Dimension historySize = history.getContentSize();
    final Dimension inputSize = input.getContentSize();

    var scrollBarNeeded = historySize.width > panelSize.width || inputSize.width > panelSize.width;
    if (scrollBarNeeded && myScrollBar.isVisible()) {
      Dimension size = myScrollBar.getPreferredSize();
      if (panelSize.height < size.height) return;
      panelSize.height -= size.height;
      myScrollBar.setBounds(0, panelSize.height, panelSize.width, size.height);
    }
    if (panelSize.getHeight() <= 0) {
      return;
    }

    // deal with width
    if (myForceAdditionalColumnsUsage &&
        !history.isDisposed() &&
        !input.isDisposed()) {
      history.getSoftWrapModel().forceAdditionalColumnsUsage();

      int minAdditionalColumns = 2;
      // calculate content size without additional columns except minimal amount
      int historySpaceWidth = EditorUtil.getPlainSpaceWidth(history);
      historySize.width += historySpaceWidth * (minAdditionalColumns - history.getSettings().getAdditionalColumnsCount());
      // calculate content size without additional columns except minimal amount
      int inputSpaceWidth = EditorUtil.getPlainSpaceWidth(input);
      inputSize.width += inputSpaceWidth * (minAdditionalColumns - input.getSettings().getAdditionalColumnsCount());
      // calculate additional columns according to the corresponding width
      int max = Math.max(historySize.width, inputSize.width);
      history.getSettings().setAdditionalColumnsCount(minAdditionalColumns + (max - historySize.width) / historySpaceWidth);
      input.getSettings().setAdditionalColumnsCount(minAdditionalColumns + (max - inputSize.width) / inputSpaceWidth);
    }

    int newInputHeight;
    // deal with height, WEB-11122 we cannot trust editor width - it could be 0 in case of soft wrap even if editor has text
    if (history.getDocument().getLineCount() == 0) {
      historySize.height = 0;
    }

    int minHistoryHeight = historySize.height > 0 ? myMinLineCount * history.getLineHeight() : 0;
    int minInputHeight = input.isViewer() ? 0 : input.getLineHeight();
    final int inputPreferredHeight = input.isViewer() ? 0 : Math.max(minInputHeight, inputSize.height);
    final int historyPreferredHeight = Math.max(minHistoryHeight, historySize.height);
    if (panelSize.height < minInputHeight) {
      newInputHeight = panelSize.height;
    }
    else if (panelSize.height < inputPreferredHeight) {
      newInputHeight = panelSize.height - minHistoryHeight;
    }
    else if (panelSize.height < (inputPreferredHeight + historyPreferredHeight) || inputPreferredHeight == 0) {
      newInputHeight = inputPreferredHeight;
    }
    else {
      newInputHeight = panelSize.height - historyPreferredHeight;
    }

    int oldHistoryHeight = history.getComponent().getHeight();
    int newHistoryHeight = panelSize.height - newInputHeight;

    var normalizedHeights = normalizeHeights(newHistoryHeight, newInputHeight);
    newHistoryHeight = normalizedHeights.historyConsoleHeight;
    newInputHeight = normalizedHeights.inputConsoleHeight;

    // apply new bounds & scroll history viewer
    input.getComponent().setBounds(0, newHistoryHeight, panelSize.width, newInputHeight);
    history.getComponent().setBounds(0, 0, panelSize.width, newHistoryHeight);
    input.getComponent().doLayout();
    history.getComponent().doLayout();
    if (newHistoryHeight < oldHistoryHeight) {
      JViewport viewport = history.getScrollPane().getViewport();
      Point position = viewport.getViewPosition();
      position.translate(0, oldHistoryHeight - newHistoryHeight);
      viewport.setViewPosition(position);
    }
  }

  protected HeightOfComponents normalizeHeights(int newHistoryHeight, int newInputHeight) {
    var history = myFirst;
    int delta = newHistoryHeight - ((newHistoryHeight / history.getLineHeight()) * history.getLineHeight());
    return new HeightOfComponents(newHistoryHeight - delta, newInputHeight + delta);
  }

  protected static final class HeightOfComponents {
    public int historyConsoleHeight, inputConsoleHeight;

    public HeightOfComponents(int historyHeight, int inputHeight) {
      historyConsoleHeight = historyHeight;
      inputConsoleHeight = inputHeight;
    }
  }
}
