package org.hanuna.gitalk.ui.render;

import org.hanuna.gitalk.printmodel.SpecialPrintElement;
import org.hanuna.gitalk.ui.render.painters.GraphCellPainter;
import org.hanuna.gitalk.ui.render.painters.RefPainter;
import org.hanuna.gitalk.ui.tables.GraphCommitCell;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;

import static org.hanuna.gitalk.ui.render.PrintParameters.HEIGHT_CELL;
import static org.hanuna.gitalk.ui.render.PrintParameters.WIDTH_NODE;

/**
 * @author erokhins
 */
public class GraphCommitCellRender implements TableCellRenderer {
  public static final Color MARKED_BACKGROUND = new Color(0xB6, 0xE4, 0xFF);
  public static final Color APPLIED_BACKGROUND = new Color(0x92, 0xF5, 0x8F);
  private final GraphCellPainter graphPainter;
  private final RefPainter refPainter = new RefPainter();
  private ExtDefaultCellRender cellRender = new ExtDefaultCellRender();

  public GraphCommitCellRender(GraphCellPainter graphPainter) {
    this.graphPainter = graphPainter;
  }

  private GraphCommitCell getAssertGraphCommitCell(Object value) {
    return (GraphCommitCell)value;
  }

  protected int getLeftPadding(JTable table, @Nullable Object value) {
    GraphCommitCell cell = getAssertGraphCommitCell(value);

    if (cell == null) {
      return 0;
    }

    FontRenderContext fontContext = ((Graphics2D)table.getGraphics()).getFontRenderContext();
    int refPadding = refPainter.padding(cell.getRefsToThisCommit(), fontContext);

    int countCells = cell.getPrintCell().countCell();
    int graphPadding = countCells * WIDTH_NODE;

    return refPadding + graphPadding;
  }

  protected String getCellText(JTable table, @Nullable Object value) {
    GraphCommitCell cell = getAssertGraphCommitCell(value);
    if (cell == null) {
      return "!!! No cell for value: " + value;
    }
    else {
      return cell.getText();
    }
  }

  protected void additionPaint(Graphics g, JTable table, @Nullable Object value) {
    GraphCommitCell cell = getAssertGraphCommitCell(value);
    if (cell == null) {
      return;
    }

    BufferedImage image = new BufferedImage(1000, HEIGHT_CELL, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = image.createGraphics();
    g2.setBackground(new Color(0, 0, 0, 0));

    graphPainter.draw(g2, cell.getPrintCell());

    int countCells = cell.getPrintCell().countCell();
    int padding = countCells * WIDTH_NODE;
    refPainter.draw(g2, cell.getRefsToThisCommit(), padding);

    g.drawImage(image, 0, 0, null);
  }

  protected boolean isMarked(JTable table, @Nullable Object value) {
    GraphCommitCell cell = getAssertGraphCommitCell(value);
    if (cell == null) {
      return false;
    }
    for (SpecialPrintElement printElement : cell.getPrintCell().getSpecialPrintElements()) {
      if (printElement.getType() == SpecialPrintElement.Type.COMMIT_NODE && printElement.isMarked()) {
        return true;
      }
    }
    return false;
  }

  protected GraphCommitCell.Kind getKind(JTable table, @Nullable Object value) {
    GraphCommitCell cell = getAssertGraphCommitCell(value);
    if (cell == null) {
      return GraphCommitCell.Kind.NORMAL;
    }

    return cell.getKind();
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, @Nullable Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    return cellRender.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
  }

  public class ExtDefaultCellRender extends DefaultTableCellRenderer {
    private JTable table;
    private Object value;

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      this.table = table;
      this.value = value;
      super.getTableCellRendererComponent(table, getCellText(table, value), isSelected, hasFocus, row, column);
      if (isMarked(table, value) && !isSelected) {
        setBackground(MARKED_BACKGROUND);
      }
      else {
        setBackground(isSelected ? table.getSelectionBackground() : Color.WHITE);
      }
      Border paddingBorder = BorderFactory.createEmptyBorder(0, getLeftPadding(table, value), 0, 0);
      this.setBorder(BorderFactory.createCompoundBorder(this.getBorder(), paddingBorder));

      GraphCommitCell.Kind kind = getKind(table, value);
      Color textColor = isSelected ? table.getSelectionForeground() : Color.BLACK;
      switch (kind) {
        case APPLIED:
          setBackground(APPLIED_BACKGROUND);
          break;
        case NORMAL:
          setForeground(textColor);
          break;
        case PICK:
          setFont(getFont().deriveFont(Font.BOLD));
          setForeground(textColor);
          break;
        case FIXUP:
          setFont(getFont().deriveFont(Font.BOLD));
          setForeground(Color.GRAY);
          break;
        case REWORD:
          setFont(getFont().deriveFont(Font.BOLD));
          setForeground(Color.blue);
          break;
      }

      return this;
    }


    @Override
    public void paint(Graphics g) {
      super.paint(g);
      additionPaint(g, table, value);
    }
  }
}
