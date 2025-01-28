package com.intellij.database.run.ui.grid;

import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.function.Supplier;

public abstract class GridRowHeader extends JComponent {
  private static final int MIN_PREFERRED_WIDTH = 15;
  private final CellRendererPane myRendererPane = new CellRendererPane();
  private final Supplier<Boolean> myPaintHorizontalLines;
  private final Supplier<Boolean> myPaintLastHorizontalLine;
  private int myPreferredWidth = 1;

  protected GridRowHeader(@NotNull JTable table,
                          @NotNull Supplier<Boolean> paintHorizontalLines,
                          @NotNull Supplier<Boolean> paintLastHorizontalLine) {
    myPaintHorizontalLines = paintHorizontalLines;
    myPaintLastHorizontalLine = paintLastHorizontalLine;
    add(myRendererPane);
    initListeners(table);
  }

  public abstract @NotNull RowHeaderCellRenderer getCellRenderer();

  public abstract @NotNull JTable getTable();

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(myPreferredWidth, getTable().getHeight());
  }

  @Override
  protected void paintComponent(Graphics g) {
    final Graphics2D g2 = (Graphics2D)g;
    GraphicsUtil.setupAntialiasing(g);

    super.paintComponent(g);

    final JTable table = getTable();
    final Rectangle clip = g.getClipBounds();
    final int rowMargin = table.getRowMargin();
    final BasicStroke gridLineStroke = new BasicStroke(rowMargin);

    CellRenderingUtils.processVisibleRows(table, g, new CellRenderingUtils.RowProcessor() {
      @Override
      public boolean process(int row, int y) {
        int rowHeight = table.getRowHeight(row);
        int cellHeight = Math.max(0, rowHeight - rowMargin);

        Component cellRenderer = getCellRenderer().getRendererComponent(row, true);
        myRendererPane.paintComponent(g2, cellRenderer, GridRowHeader.this, 0, y, getWidth(), cellHeight, true);

        int gridY = y + cellHeight + rowMargin / 2;
        Stroke backupStroke = g2.getStroke();
        Color backupColor = g2.getColor();

        if (myPaintHorizontalLines.get() || myPaintLastHorizontalLine.get() && row == table.getRowCount() - 1) {
          g2.setStroke(gridLineStroke);
          g2.setColor(table.getGridColor());
          SwingUtilities2.drawHLine(g2, clip.x, clip.x + clip.width, gridY);
          g2.setStroke(backupStroke);
          g2.setColor(backupColor);
        }
        return true;
      }
    });
  }

  private void initListeners(JTable table) {
    table.getModel().addTableModelListener(e -> updatePreferredSize());
    addPropertyChangeListener("rowHeight", e -> updatePreferredSize());
    table.addComponentListener(new ComponentAdapter() {
      int myPreviousTableHeight;

      @Override
      public void componentResized(ComponentEvent e) {
        Component c = e.getComponent();
        if (myPreviousTableHeight != c.getHeight()) {
          updatePreferredSize(getPreferredSize().width, false);
          myPreviousTableHeight = c.getHeight();
        }
      }
    });
  }

  public void updatePreferredSize() {
    Insets insets = getInsets();
    updatePreferredSize(insets.left + insets.right + calcPreferredWidthWithoutInsets(), true);
  }

  protected void updatePreferredSize(int preferredWidth, boolean checkMinWidth) {
    if (checkMinWidth && preferredWidth < MIN_PREFERRED_WIDTH) preferredWidth = MIN_PREFERRED_WIDTH;
    Dimension prevSize = getPreferredSize();
    myPreferredWidth = preferredWidth;
    Dimension newSize = getPreferredSize();
    firePropertyChange("preferredSize", prevSize, newSize);
  }

  protected int calcPreferredWidthWithoutInsets() {
    int maxCellRendererWidth = 0;
    for (int row = 0; row < getTable().getModel().getRowCount(); row++) {
      Component renderer = getCellRenderer().getRendererComponent(row, false);
      Dimension preferredSize = renderer.getPreferredSize();
      if (preferredSize.width > maxCellRendererWidth) {
        maxCellRendererWidth = preferredSize.width;
      }
    }
    return Math.max(1, maxCellRendererWidth);
  }

  public interface RowHeaderCellRenderer {
    Component getRendererComponent(int row, boolean forDisplay);
  }
}
