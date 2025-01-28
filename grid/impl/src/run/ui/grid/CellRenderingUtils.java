package com.intellij.database.run.ui.grid;

import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class CellRenderingUtils {

  public static final Insets NAME_LABEL_INSETS = JBUI.insets(2, 4, 2, 0);
  public static final Insets SORT_LABEL_INSETS = JBUI.insets(2, 2, 2, 3);
  // same as above with top/bottom set to 0 for transposed row header
  public static final Insets NAME_LABEL_ROW_INSETS = JBUI.insets(0, 3, 0, 0);
  public static final Insets SORT_LABEL_ROW_INSETS = JBUI.insets(0, 2, 0, 3);

  public static void paintCellEffect(@NotNull Graphics g, @NotNull Rectangle cellRect, @NotNull CellAttributes attributes, @Nullable Color effectColor) {
    if (attributes.isUnderlined() && effectColor != null) {
      g.setColor(effectColor);
      g.drawLine((int)cellRect.getMinX(), (int)cellRect.getMaxY()-1, (int)cellRect.getMaxX(), (int)cellRect.getMaxY()-1);
    }
  }

  public static void paintCellEffect(@NotNull Graphics g, @NotNull Rectangle cellRect, @NotNull CellAttributes attributes) {
    paintCellEffect(g, cellRect, attributes, attributes.getEffectColor());
  }

  public interface RowProcessor {
    boolean process(int row, int y);
  }

  public static void processVisibleRows(@NotNull JTable table, @NotNull Graphics g, @NotNull RowProcessor proc) {
    Rectangle clip = g.getClipBounds();
    int rowCount = table.getRowCount();
    int startY = table.getRowMargin() / 2;
    int startRow = 0;
    while (startRow < rowCount) {
      int rowHeight = table.getRowHeight(startRow);
      if (startY + rowHeight >= clip.y) {
        break;
      }
      startY += rowHeight;
      startRow++;
    }

    int y = startY;
    for (int row = startRow; row < rowCount && y <= clip.y + clip.height; row++) {
      if (!proc.process(row, y)) break;
      int rowHeight = table.getRowHeight(row);
      y += rowHeight;
    }

  }
}
