package com.intellij.history.integration.ui.views.table;

import com.intellij.ui.SideBorder;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class ShiftedCellRendererWrapper extends JPanel implements TableCellRenderer {
  private TableCellRenderer myDataRenderer;
  private JComponent myRendererComponent;
  private final ArrowBorder myBorder = new ArrowBorder();
  private final Border myEmptyBorder;
  private final JComponent myPrevBottomRenderer = new JPanel();
  private final SideBorder myPrevBottomBorder = new SideBorder(null, SideBorder.TOP);

  public ShiftedCellRendererWrapper() {
    super(new BorderLayout());
    Insets insets = myBorder.getBorderInsets(this);
    myEmptyBorder = BorderFactory.createEmptyBorder(0, insets.left, 0, insets.right);
    myPrevBottomRenderer.setBorder(myPrevBottomBorder);
  }

  public void setDataRenderer(TableCellRenderer dataRenderer) {
    myDataRenderer = dataRenderer;
  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (myRendererComponent != null) remove(myRendererComponent);
    myRendererComponent = null;

    myRendererComponent = (JComponent)myDataRenderer.
      getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    if (myRendererComponent == null) return null;
    myBorder.setColor(table.getGridColor());
    setForeground(myRendererComponent.getForeground());
    setFont(myRendererComponent.getFont());
    setBorder(myBorder);

    if (myRendererComponent != null) add(myRendererComponent, BorderLayout.CENTER);
    setBackground(table.getBackground());
    return this;
  }
}
