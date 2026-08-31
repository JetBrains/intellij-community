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

public class TableScrollPane extends JBScrollPane {
  protected final TableResultView myResultView;
  private boolean myAddSpaceForHorizontalScrollbar;
  private boolean myFrozenHorizontalScrollBarVisible;
  private @Nullable JComponent myOverlaidFrozenCorner;
  private boolean myOverlaidFrozenCornerWasOpaque;

  public TableScrollPane(@NotNull TableResultView table,
                         @NotNull DataGrid grid,
                         @NotNull JComponent topLeftCornerComponent,
                         @Nullable JBAutoScroller.AutoscrollLocker locker) {
    myResultView = table;
    setBorder(BorderFactory.createEmptyBorder());
    setViewportView(myResultView);
    setColumnHeaderView(myResultView.getTableHeader());
    setCorner(UPPER_LEADING_CORNER, topLeftCornerComponent);
    setupColumnScroller(locker);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        ApplicationManager.getApplication().invokeLater(() -> IdeFocusManager.getInstance(grid.getProject()).requestFocus(table, true));
      }
    });
  }

  @Override
  protected JViewport createViewport() {
    return new JBViewport() {
      @Override
      public Color getBackground() {
        return myResultView != null ? myResultView.getComponent().getBackground() : super.getBackground();
      }
    };
  }

  @Override
  public @NotNull JScrollBar createHorizontalScrollBar() {
    return new JBScrollBar(Adjustable.HORIZONTAL) {
      @Override
      public void setVisible(boolean visible) {
        super.setVisible(visible && (!myFrozenHorizontalScrollBarVisible || hasScrollableRange(this)));
      }
    };
  }

  boolean isFrozenHorizontalScrollBarVisible() {
    return myFrozenHorizontalScrollBarVisible;
  }

  /**
   * The scroll pane still reserves the horizontal-scrollbar band so its lower leading corner can host the frozen
   * strip's scrollbar, but an unrelated full-thumb scrollbar must not be exposed when the main table itself fits.
   */
  void setFrozenHorizontalScrollBarVisible(boolean visible) {
    myFrozenHorizontalScrollBarVisible = visible;
    if (!visible) restoreOverlaidFrozenCorner();
  }

  private static boolean hasScrollableRange(@NotNull JScrollBar scrollBar) {
    BoundedRangeModel model = scrollBar.getModel();
    return (long)model.getMaximum() - model.getMinimum() > model.getExtent();
  }

  @Override
  public void doLayout() {
    super.doLayout();
    if (myFrozenHorizontalScrollBarVisible) {
      // HORIZONTAL_SCROLLBAR_ALWAYS reserves a host for the frozen scrollbar. Decide whether the main scrollbar is
      // useful from its final model, after the scroll pane has laid out and synchronized the viewport.
      getHorizontalScrollBar().setVisible(true);
    }
    updateFrozenScrollBarCorner();
  }

  /** Gives the frozen scrollbar a visible overlay host when the platform does not reserve a scrollbar band. */
  private void updateFrozenScrollBarCorner() {
    Component corner = getCorner(LOWER_LEADING_CORNER);
    JViewport rowHeader = getRowHeader();
    if (!myFrozenHorizontalScrollBarVisible || !(corner instanceof JComponent component) || rowHeader == null ||
        corner.getWidth() > 0 && corner.getHeight() > 0) {
      restoreOverlaidFrozenCorner();
      return;
    }

    int height = Math.min(corner.getPreferredSize().height, rowHeader.getHeight());
    if (rowHeader.getWidth() <= 0 || height <= 0) {
      restoreOverlaidFrozenCorner();
      return;
    }

    if (myOverlaidFrozenCorner != component) {
      restoreOverlaidFrozenCorner();
      myOverlaidFrozenCorner = component;
      myOverlaidFrozenCornerWasOpaque = component.isOpaque();
    }
    component.setOpaque(false);
    component.setBounds(rowHeader.getX(), rowHeader.getY() + rowHeader.getHeight() - height, rowHeader.getWidth(), height);
    if (component.getParent() == this) setComponentZOrder(component, 0);
  }

  private void restoreOverlaidFrozenCorner() {
    if (myOverlaidFrozenCorner == null) return;
    myOverlaidFrozenCorner.setOpaque(myOverlaidFrozenCornerWasOpaque);
    myOverlaidFrozenCorner = null;
  }

  public void addSpaceForHorizontalScrollbar(boolean v) {
    myAddSpaceForHorizontalScrollbar = v;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    int updatedHeight = size.height + myResultView.getRowHeight() * myResultView.getAdditionalRowsCount();
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

  protected void setupColumnScroller(@Nullable JBAutoScroller.AutoscrollLocker locker) {
    JBAutoScroller.installOn(myResultView.getComponent(), locker);
    JViewport header = getRowHeader();
    JComponent view = header == null ? null : (JComponent)header.getView();
    if (view != null) {
      JBAutoScroller.installOn(view, new JBAutoScroller.DefaultScrollDeltaProvider() {
        @Override
        public int getHorizontalScrollDelta(MouseEvent e) {
          return 0;
        }
      });
    }

    JBAutoScroller.installOn(myResultView.getTableHeader(), new JBAutoScroller.DefaultScrollDeltaProvider() {
      @Override
      public int getVerticalScrollDelta(MouseEvent e) {
        return 0;
      }

      @Override
      public int getHorizontalScrollDelta(MouseEvent e) {
        JTableHeader header = myResultView.getTableHeader();
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
        JTableHeader header = myResultView.getTableHeader();
        TableColumnModel columnModel = myResultView.getColumnModel();

        int targetColumnX = 0;
        for (int i = 0; i < myResultView.getColumnCount(); i++) {
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
