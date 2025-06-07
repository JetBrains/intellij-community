package com.intellij.database.datagrid;

import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.ui.components.JBLoadingPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public interface GridPanel extends EditorColorsListener {
  @NotNull
  JBLoadingPanel getComponent();

  @Nullable
  Component getTopComponent();

  void setTopComponent(@Nullable Component topComponent);

  void setRightHeaderComponent(@Nullable Component topComponent);

  void setBottomHeaderComponent(@Nullable JComponent topComponent);

  @Nullable
  JComponent getBottomHeaderComponent();

  @Nullable
  Component getSecondTopComponent();

  @NotNull
  JComponent getCenterComponent();

  void setSecondTopComponent(@Nullable Component topComponent);

  @Nullable
  RemovableView getSideView(@NotNull ViewPosition viewPosition);
  void removeSideView(@NotNull RemovableView view);
  void putSideView(@NotNull RemovableView view, @NotNull ViewPosition newPosition, @Nullable ViewPosition oldPosition);
  @Nullable
  ViewPosition locateSideView(@NotNull RemovableView view);

  enum ViewPosition {
    RIGHT,
    BOTTOM
  }
}
