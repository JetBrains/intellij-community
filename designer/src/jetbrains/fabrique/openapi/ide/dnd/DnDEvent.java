package jetbrains.fabrique.openapi.ide.dnd;

import jetbrains.fabrique.util.awt.RelativePoint;
import jetbrains.fabrique.util.awt.RelativeRectangle;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * @author mike
 */
public interface DnDEvent extends Transferable {
  DnDAction getAction();

  void updateAction(DnDAction action);

  Object getAttachedObject();

  void setDropPossible(boolean possible, String aExpectedResult);

  void setDropPossible(String aExpectedResult, DropActionHandler aHandler);

  String getExpectedDropResult();

  DataFlavor[] getTransferDataFlavors();

  boolean isDataFlavorSupported(DataFlavor flavor);

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
}
