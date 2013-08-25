package org.hanuna.gitalk.ui.render.painters;

import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hanuna.gitalk.ui.render.PrintParameters.HEIGHT_CELL;

/**
 * @author erokhins
 */
public class RefPainter {
  private static final int RECTANGLE_X_PADDING = 4;
  private static final int RECTANGLE_Y_PADDING = 3;
  private static final int REF_PADDING = 13;

  private static final int ROUND_RADIUS = 10;

  private static final Font DEFAULT_FONT = new Font("Arial", Font.PLAIN, 12);
  private static final Color DEFAULT_FONT_COLOR = Color.black;

  private double paddingStr(@NotNull String str, @NotNull FontRenderContext renderContext) {
    return DEFAULT_FONT.getStringBounds(str, renderContext).getWidth() + REF_PADDING;
  }

  private void drawText(@NotNull Graphics2D g2, @NotNull String str, int padding) {
    FontMetrics metrics = g2.getFontMetrics();
    g2.setColor(DEFAULT_FONT_COLOR);
    int x = padding + REF_PADDING / 2;
    int y = HEIGHT_CELL / 2 + (metrics.getAscent() - metrics.getDescent()) / 2;
    g2.drawString(str, x, y);
  }

  private Color refBackgroundColor(@NotNull VcsRef ref) {
    switch (ref.getType()) {
      case HEAD:
        return Color.GREEN;
      case LOCAL_BRANCH:
        return Color.orange;
      case BRANCH_UNDER_INTERACTIVE_REBASE:
        return Color.yellow;
      case REMOTE_BRANCH:
        return Color.cyan;
      case TAG:
        return Color.magenta;
      case STASH:
        return Color.red;
      default:
        throw new IllegalArgumentException();
    }
  }

  private int draw(@NotNull Graphics2D g2, @NotNull VcsRef ref, int padding) {
    FontMetrics metrics = g2.getFontMetrics();
    int x = padding + REF_PADDING / 2 - RECTANGLE_X_PADDING;
    int y = RECTANGLE_Y_PADDING;
    int width = metrics.stringWidth(ref.getName()) + 2 * RECTANGLE_X_PADDING;
    int height = HEIGHT_CELL - 2 * RECTANGLE_Y_PADDING;
    RoundRectangle2D rectangle2D = new RoundRectangle2D.Double(x, y, width, height, ROUND_RADIUS, ROUND_RADIUS);

    g2.setColor(refBackgroundColor(ref));
    g2.fill(rectangle2D);

    g2.setColor(Color.black);
    g2.draw(rectangle2D);

    drawText(g2, ref.getName(), padding);
    return x;
  }

  public int padding(@NotNull List<VcsRef> refs, @NotNull FontRenderContext renderContext) {
    float p = 0;
    for (VcsRef ref : refs) {
      p += paddingStr(ref.getName(), renderContext);
    }
    return Math.round(p);
  }

  public Map<Integer, VcsRef> draw(@NotNull Graphics2D g2, @NotNull List<VcsRef> refs, int startPadding) {
    float currentPadding = startPadding;
    g2.setFont(DEFAULT_FONT);
    g2.setStroke(new BasicStroke(1.5f));
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    FontRenderContext renderContext = g2.getFontRenderContext();
    Map<Integer, VcsRef> positions = new HashMap<Integer, VcsRef>();
    for (VcsRef ref : refs) {
      int x = draw(g2, ref, (int)currentPadding);
      positions.put(x, ref);
      currentPadding += paddingStr(ref.getName(), renderContext);
    }
    return positions;
  }

}
