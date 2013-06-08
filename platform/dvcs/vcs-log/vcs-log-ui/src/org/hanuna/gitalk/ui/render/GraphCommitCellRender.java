package org.hanuna.gitalk.ui.render;

import org.hanuna.gitalk.printmodel.SpecialPrintElement;
import org.hanuna.gitalk.ui.render.painters.GraphCellPainter;
import org.hanuna.gitalk.ui.render.painters.RefPainter;
import org.hanuna.gitalk.ui.tables.GraphCommitCell;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;

import static org.hanuna.gitalk.ui.render.Print_Parameters.HEIGHT_CELL;
import static org.hanuna.gitalk.ui.render.Print_Parameters.WIDTH_NODE;

/**
 * @author erokhins
 */
public class GraphCommitCellRender extends AbstractPaddingCellRender {
  private final GraphCellPainter graphPainter;
  private final RefPainter refPainter = new RefPainter();

  public GraphCommitCellRender(GraphCellPainter graphPainter) {
    this.graphPainter = graphPainter;
  }

  private GraphCommitCell getAssertGraphCommitCell(Object value) {
    return (GraphCommitCell)value;
  }

  @Override
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

  @Override
  protected String getCellText(JTable table, @Nullable Object value) {
    GraphCommitCell cell = getAssertGraphCommitCell(value);
    if (cell == null) {
      return "!!! No cell for value: " + value;
    }
    else {
      return cell.getText();
    }
  }

  @Override
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

  @Override
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

  @Override
  protected GraphCommitCell.Kind getKind(JTable table, @Nullable Object value) {
    GraphCommitCell cell = getAssertGraphCommitCell(value);
    if (cell == null) {
      return GraphCommitCell.Kind.NORMAL;
    }

    return cell.getKind();
  }
}
