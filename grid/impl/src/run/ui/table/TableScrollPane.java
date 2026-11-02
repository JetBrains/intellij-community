package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBAutoScroller;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBViewport;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JViewport;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.Adjustable;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The view's component: a scroll pane over the main table and its header, with Select All in the upper leading corner. While columns are
 * pinned, the pinned table sits in its row header, and the pinned columns' scroll bar in the lower leading corner.
 */
public class TableScrollPane extends JBScrollPane {
  /** The main table, which the viewport shows; the pinned table, while there is one, sits in the row header. */
  final GridTable myMainTable;
  private boolean myAddSpaceForHorizontalScrollbar;
  private boolean myPinnedScrollBarVisible;
  private @Nullable JComponent myOverlaidCorner;
  private boolean myOverlaidCornerWasOpaque;

  TableScrollPane(@NotNull GridTable mainTable,
                  @NotNull DataGrid grid,
                  @NotNull JComponent upperLeadingCorner,
                  @Nullable JBAutoScroller.AutoscrollLocker locker) {
    myMainTable = mainTable;
    setBorder(BorderFactory.createEmptyBorder());
    setViewportView(myMainTable);
    setColumnHeaderView(myMainTable.getTableHeader());
    setCorner(UPPER_LEADING_CORNER, upperLeadingCorner);
    installDragAutoScrollers(locker);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        ApplicationManager.getApplication().invokeLater(
          () -> IdeFocusManager.getInstance(grid.getProject()).requestFocus(TableScrollPane.this, true));
      }
    });
  }

  @Override
  protected JViewport createViewport() {
    return new JBViewport() {
      @Override
      public Color getBackground() {
        return myMainTable != null ? myMainTable.getComponent().getBackground() : super.getBackground();
      }
    };
  }

  @Override
  public @NotNull JScrollBar createHorizontalScrollBar() {
    return new JBScrollBar(Adjustable.HORIZONTAL) {
      @Override
      public void setVisible(boolean visible) {
        super.setVisible(visible && (!myPinnedScrollBarVisible || hasScrollableRange(this)));
      }
    };
  }

  boolean isPinnedScrollBarVisible() {
    return myPinnedScrollBarVisible;
  }

  /**
   * Whether the pinned columns overflow and show their own scroll bar in the lower leading corner. While they do, the main table's scroll
   * bar shows only when the main table can scroll.
   */
  void setPinnedScrollBarVisible(boolean visible) {
    myPinnedScrollBarVisible = visible;
    if (!visible) restoreOverlaidCorner();
  }

  private static boolean hasScrollableRange(@NotNull JScrollBar scrollBar) {
    BoundedRangeModel model = scrollBar.getModel();
    return (long)model.getMaximum() - model.getMinimum() > model.getExtent();
  }

  @Override
  public void doLayout() {
    super.doLayout();
    if (myPinnedScrollBarVisible) {
      // HORIZONTAL_SCROLLBAR_ALWAYS keeps a band for the pinned columns' scroll bar. Decide whether the main scrollbar is
      // useful from its final model, after the scroll pane has laid out and synchronized the viewport.
      getHorizontalScrollBar().setVisible(true);
    }
    overlayPinnedScrollBarCorner();
  }

  /**
   * When the layout leaves the lower leading corner no room, lays it over the bottom of the row header. That corner holds the pinned
   * columns' scroll bar.
   */
  private void overlayPinnedScrollBarCorner() {
    Component corner = getCorner(LOWER_LEADING_CORNER);
    JViewport rowHeader = getRowHeader();
    if (!myPinnedScrollBarVisible || !(corner instanceof JComponent component) || rowHeader == null ||
        corner.getWidth() > 0 && corner.getHeight() > 0) {
      restoreOverlaidCorner();
      return;
    }

    int height = Math.min(corner.getPreferredSize().height, rowHeader.getHeight());
    if (rowHeader.getWidth() <= 0 || height <= 0) {
      restoreOverlaidCorner();
      return;
    }

    if (myOverlaidCorner != component) {
      restoreOverlaidCorner();
      myOverlaidCorner = component;
      myOverlaidCornerWasOpaque = component.isOpaque();
    }
    component.setOpaque(false);
    component.setBounds(rowHeader.getX(), rowHeader.getY() + rowHeader.getHeight() - height, rowHeader.getWidth(), height);
    if (component.getParent() == this) setComponentZOrder(component, 0);
  }

  private void restoreOverlaidCorner() {
    if (myOverlaidCorner == null) return;
    myOverlaidCorner.setOpaque(myOverlaidCornerWasOpaque);
    myOverlaidCorner = null;
  }

  public void addSpaceForHorizontalScrollbar(boolean v) {
    myAddSpaceForHorizontalScrollbar = v;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    int updatedHeight = size.height + myMainTable.getRowHeight() * myMainTable.getAdditionalRowsCount();
    JScrollBar hsb = getHorizontalScrollBar();
    if (myAddSpaceForHorizontalScrollbar && hsb != null && hsb.isOpaque()) {
      updatedHeight += hsb.getPreferredSize().height;
    }

    return new Dimension(size.width, updatedHeight);
  }

  @Override
  public Insets getInsets() {
    return JBInsets.emptyInsets();
  }

  public void setFlipControls(boolean flip) {
    putClientProperty(Flip.class, flip ? Flip.HORIZONTAL : null);
  }

  private void installDragAutoScrollers(@Nullable JBAutoScroller.AutoscrollLocker locker) {
    JBAutoScroller.installOn(myMainTable.getComponent(), locker);
    JBAutoScroller.installOn(myMainTable.getTableHeader(), new JBAutoScroller.DefaultScrollDeltaProvider() {
      @Override
      public int getVerticalScrollDelta(MouseEvent e) {
        return 0;
      }

      @Override
      public int getHorizontalScrollDelta(MouseEvent e) {
        JTableHeader header = myMainTable.getTableHeader();
        TableColumn draggedColumn = header.getDraggedColumn();
        if (draggedColumn != null) {
          Rectangle visibleRect = header.getVisibleRect();
          int leftmost = visibleRect.x;
          int rightmost = visibleRect.x + visibleRect.width - 1;

          Rectangle draggedRect = computeColumnHeaderRectangle(draggedColumn);
          int left = draggedRect.x + header.getDraggedDistance();
          int right = left + draggedRect.width - 1;

          return right > rightmost ? right - rightmost :
                 left < leftmost ? left - leftmost : 0;
        }
        return super.getHorizontalScrollDelta(e);
      }

      private Rectangle computeColumnHeaderRectangle(TableColumn targetColumn) {
        JTableHeader header = myMainTable.getTableHeader();
        TableColumnModel columnModel = myMainTable.getColumnModel();

        int targetColumnX = 0;
        for (int i = 0; i < myMainTable.getColumnCount(); i++) {
          TableColumn column = columnModel.getColumn(i);
          if (column == targetColumn) {
            break;
          }
          targetColumnX += column.getWidth();
        }

        return new Rectangle(targetColumnX, 0, targetColumn.getWidth(), header.getHeight());
      }
    });
  }

  @Override
  public void setComponentOrientation(ComponentOrientation co) {
    super.setComponentOrientation(co);
    flipCorners(UPPER_LEFT_CORNER, UPPER_RIGHT_CORNER);
    flipCorners(LOWER_LEFT_CORNER, LOWER_RIGHT_CORNER);
  }

  private void flipCorners(String corner1, String corner2) {
    Component c1 = getCorner(corner1);
    Component c2 = getCorner(corner2);
    setCorner(corner1, c2);
    setCorner(corner2, c1);
  }

  public boolean isFlipped() {
    Object property = getClientProperty(JBScrollPane.Flip.class);
    return property == Flip.HORIZONTAL || property == Flip.BOTH;
  }
}
