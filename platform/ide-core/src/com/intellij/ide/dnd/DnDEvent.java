// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public interface DnDEvent extends Transferable, UserDataHolder {
  DnDAction getAction();

  void updateAction(@NotNull DnDAction action);

  Object getAttachedObject();

  void setDropPossible(boolean possible, @Nullable String aExpectedResult);

  void setDropPossible(boolean possible);

  void setDropPossible(String aExpectedResult, DropActionHandler aHandler);

  @NlsContexts.PopupContent String getExpectedDropResult();

  @Override
  DataFlavor[] getTransferDataFlavors();

  @Override
  boolean isDataFlavorSupported(DataFlavor flavor);

  @Override
  Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException;

  boolean isDropPossible();

  Point getOrgPoint();

  void setOrgPoint(Point orgPoint);

  Point getPoint();

  Point getPointOn(Component aComponent);

  boolean canHandleDrop();

  Component getHandlerComponent();

  Component getCurrentOverComponent();

  void setHighlighting(Component aComponent, int aType);

  void setHighlighting(RelativeRectangle rectangle, int aType);

  void setHighlighting(JLayeredPane layeredPane, RelativeRectangle rectangle, int aType);

  void setAutoHideHighlighterInDrop(boolean aValue);

  void hideHighlighter();

  void setLocalPoint(Point localPoint);

  Point getLocalPoint();

  RelativePoint getRelativePoint();

  void clearDelegatedTarget();

  boolean wasDelegated();

  DnDTarget getDelegatedTarget();

  boolean delegateUpdateTo(DnDTarget target);

  void delegateDropTo(DnDTarget target);

  Cursor getCursor();

  void setCursor(Cursor cursor);

  void cleanUp();

  interface DropTargetHighlightingType {
    int RECTANGLE = 1;
    int FILLED_RECTANGLE = 2;
    int H_ARROWS = 4;
    int V_ARROWS = 8;

    int TEXT = 16;
    int ERROR_TEXT = 32;
    @ApiStatus.Experimental
    int BOTTOM = 64;
  }
}
