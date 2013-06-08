package org.hanuna.gitalk.swing_ui.render;

import org.hanuna.gitalk.swing_ui.render.painters.RefPainter;
import org.hanuna.gitalk.swing_ui.tables.CommitCell;
import org.hanuna.gitalk.swing_ui.tables.GraphCommitCell;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;

/**
 * @author erokhins
 */
public class CommitCellRender extends AbstractPaddingCellRender {
  private final RefPainter refPainter = new RefPainter();

  @Override
  protected int getLeftPadding(JTable table, Object value) {
    CommitCell cell = getAssertCommitCell(value);
    if (cell == null) {
      return 0;
    }

    FontRenderContext fontContext = ((Graphics2D)table.getGraphics()).getFontRenderContext();
    return refPainter.padding(cell.getRefsToThisCommit(), fontContext);
  }

  private CommitCell getAssertCommitCell(Object value) {
    return (CommitCell)value;
  }

  @Override
  protected String getCellText(JTable table, Object value) {
    CommitCell cell = getAssertCommitCell(value);
    if (cell == null) {
      return "!!! No cell value";
    }
    return cell.getText();
  }

  @Override
  protected void additionPaint(Graphics g, JTable table, Object value) {
    CommitCell cell = getAssertCommitCell(value);
    if (cell == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D)g;
    refPainter.draw(g2, cell.getRefsToThisCommit(), 0);
  }

  @Override
  protected boolean isMarked(JTable table, Object value) {
    return false;
  }

  @Override
  protected GraphCommitCell.Kind getKind(JTable table, @Nullable Object value) {
    return GraphCommitCell.Kind.NORMAL;
  }
}
