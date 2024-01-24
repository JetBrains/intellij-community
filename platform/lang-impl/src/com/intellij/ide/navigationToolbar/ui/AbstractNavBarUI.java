// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.navigationToolbar.NavBarItem;
import com.intellij.ide.navigationToolbar.NavBarModel;
import com.intellij.ide.navigationToolbar.NavBarPanel;
import com.intellij.ide.navigationToolbar.NavBarPopup;
import com.intellij.ide.ui.UISettings;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.ScaleContextCache;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.ide.navbar.ui.UiKt.*;

/**
 * @author Konstantin Bulenkov
 * @deprecated unused in ide.navBar.v2. If you do a change here, please also update v2 implementation
 */
@Deprecated(forRemoval = true)
public abstract class AbstractNavBarUI implements NavBarUI {
  private final static Map<NavBarItem, Map<ImageType, ScaleContextCache<BufferedImage>>> cache = new ConcurrentHashMap<>();

  @Override
  public Insets getElementIpad(boolean isPopupElement) {
    return isPopupElement ? navBarPopupItemInsets()
                          : navBarItemInsets();
  }

  @Override
  public Insets getElementPadding(@NotNull NavBarItem item) {
    return navBarItemPadding(item.isInFloatingMode());
  }

  @Override
  public Font getElementFont(NavBarItem navBarItem) {
    return navBarItemFont();
  }

  @Override
  public Color getBackground(boolean selected, boolean focused) {
    return navBarItemBackground(selected, focused);
  }

  @Nullable
  @Override
  public Color getForeground(boolean selected, boolean focused, boolean inactive) {
    return defaultNavBarItemForeground(selected, focused, inactive);
  }

  @Override
  public short getSelectionAlpha() {
    return 150;
  }

  @Override
  public void doPaintNavBarItem(Graphics2D g, NavBarItem item, NavBarPanel navbar) {
    JBInsets paddings = JBInsets.create(getElementPadding(item));

    if (ExperimentalUI.isNewUI()) {
      Rectangle rect = new Rectangle(item.getSize());
      JBInsets.removeFrom(rect, paddings);

      int offset = rect.x;
      if (!item.isFirstElement()) {
        NavBarItem.CHEVRON_ICON.paintIcon(item, g, offset, rect.y + (rect.height - NavBarItem.CHEVRON_ICON.getIconHeight()) / 2);
        int delta = NavBarItem.CHEVRON_ICON.getIconWidth() + JBUI.CurrentTheme.StatusBar.Breadcrumbs.CHEVRON_INSET.get();
        offset += delta;
        rect.width -= delta;
      }

      paintHighlight(g, navbar, item, new Rectangle(offset, rect.y, rect.width, rect.height));

      boolean paintIcon = false;
      if (item.needPaintIcon()) {
        Icon icon = item.getIcon();
        if (icon != null) {
          paintIcon = true;
          offset += item.getIpad().left;
          icon.paintIcon(item, g, offset, rect.y + (rect.height - icon.getIconHeight()) / 2 + item.getVerticalIconOffset());
          offset += icon.getIconWidth();
        }
      }

      offset += paintIcon ? item.getIconTextGap() : item.getIpad().left;
      item.doPaintText(g, offset);
    }
    else {
      final boolean floating = navbar.isInFloatingMode();
      boolean toolbarVisible = UISettings.getInstance().getShowMainToolbar();
      final boolean selected = item.isSelected() && item.isFocused();
      boolean nextSelected = item.isNextSelected() && navbar.isFocused();

      ImageType type = ImageType.from(floating, toolbarVisible, selected, nextSelected);

      // see: https://github.com/JetBrains/intellij-community/pull/1111
      Map<ImageType, ScaleContextCache<BufferedImage>> cache = AbstractNavBarUI.cache.computeIfAbsent(item, k -> new HashMap<>());
      ScaleContextCache<BufferedImage> imageCache = cache.computeIfAbsent(type, k -> {
        return new ScaleContextCache<>(ctx -> {
          return drawToBuffer(item, ctx, floating, toolbarVisible, selected, nextSelected, item.isLastElement());
        });
      });
      BufferedImage image = imageCache.getOrProvide(ScaleContext.create(g));
      if (image == null) {
        return;
      }

      StartupUiUtil.drawImage(g, image, 0, 0, null);

      final int offset = item.isFirstElement() ? getFirstElementLeftOffset() : 0;
      int textOffset = paddings.width() + offset;
      if (item.needPaintIcon()) {
        Icon icon = item.getIcon();
        if (icon != null) {
          int iconOffset = paddings.left + offset;
          icon.paintIcon(item, g, iconOffset, (item.getHeight() - icon.getIconHeight()) / 2);
          textOffset += icon.getIconWidth();
        }
      }

      item.doPaintText(g, textOffset);
    }
  }

  private static void paintHighlight(Graphics2D g, NavBarPanel panel, NavBarItem item, Rectangle rectangle) {
    Color color;
    NavBarModel model = panel.getModel();
    NavBarPopup popup = panel.getNodePopup();
    if (item.isMouseHover()) {
      color = JBUI.CurrentTheme.StatusBar.Breadcrumbs.HOVER_BACKGROUND;
    }
    else if ((model.getSelectedIndex() == item.getIndex()) && item.isFocused()) {
      color = JBUI.CurrentTheme.StatusBar.Breadcrumbs.SELECTION_BACKGROUND;
    }
    else if ((model.getSelectedIndex() == item.getIndex()) && popup != null && popup.isVisible() && popup.getItemIndex() == item.getIndex()) {
      color = JBUI.CurrentTheme.StatusBar.Breadcrumbs.SELECTION_INACTIVE_BACKGROUND;
    }
    else return;

    paintHighlight(g, rectangle, color);
  }

  @Internal
  public static void paintHighlight(@NotNull Graphics2D g, @NotNull Rectangle rectangle, @NotNull Color color) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

      g2.setColor(color);

      float arc = JBUIScale.scale(4);
      RoundRectangle2D shape = new RoundRectangle2D.Float(rectangle.x, rectangle.y, rectangle.width, rectangle.height, arc, arc);
      g2.fill(shape);
    }
    finally {
      g2.dispose();
    }
  }

  @Internal
  public static BufferedImage drawToBuffer(
    @NotNull Component item,
    ScaleContext ctx,
    boolean floating,
    boolean toolbarVisible,
    boolean selected,
    boolean nextSelected,
    boolean isLastElement
  ) {
    int w = item.getWidth();
    int h = item.getHeight();
    int offset = (w - getDecorationOffset());
    int h2 = h / 2;

    BufferedImage result = ImageUtil.createImage(ctx, w, h, BufferedImage.TYPE_INT_ARGB, PaintUtil.RoundingMode.FLOOR);

    Color defaultBg = StartupUiUtil.isUnderDarcula() ? Gray._100 : JBColor.WHITE;
    final Paint bg = floating ? defaultBg : null;
    final Color selection = UIUtil.getListSelectionBackground(true);

    Graphics2D g2 = result.createGraphics();
    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);


    Path2D.Double shape = new Path2D.Double();
    shape.moveTo(0, 0);

    shape.lineTo(offset, 0);
    shape.lineTo(w, h2);
    shape.lineTo(offset, h);
    shape.lineTo(0, h);
    shape.closePath();

    Path2D.Double endShape = new Path2D.Double();
    endShape.moveTo(offset, 0);
    endShape.lineTo(w, 0);
    endShape.lineTo(w, h);
    endShape.lineTo(offset, h);
    endShape.lineTo(w, h2);
    endShape.closePath();

    if (bg != null && toolbarVisible) {
      g2.setPaint(bg);
      g2.fill(shape);
      g2.fill(endShape);
    }

    if (selected) {
      Path2D.Double focusShape = new Path2D.Double();
      if (toolbarVisible || floating) {
        focusShape.moveTo(offset, 0);
      } else {
        focusShape.moveTo(0, 0);
        focusShape.lineTo(offset, 0);
      }
      focusShape.lineTo(w - 1, h2);
      focusShape.lineTo(offset, h - 1);
      if (!toolbarVisible && !floating) {
        focusShape.lineTo(0, h - 1);

      }

      g2.setColor(selection);
      if (floating && isLastElement) {
        g2.fillRect(0, 0, w, h);
      } else {
        g2.fill(shape);
      }
    }

    if (nextSelected) {
      g2.setColor(selection);
      g2.fill(endShape);
    }

    if (!isLastElement) {
      if (!selected && !nextSelected) {
        Icon icon = AllIcons.Ide.NavBarSeparator;
        icon.paintIcon(item, g2, w - icon.getIconWidth() - JBUIScale.scale(1), h2 - icon.getIconHeight() / 2);
      }
    }

    g2.dispose();
    return result;
  }

  @Internal
  public static int getDecorationOffset() {
    return JBUIScale.scale(8);
  }

  @Internal
  public static int getFirstElementLeftOffset() {
    return JBUIScale.scale(6);
  }

  @Override
  public Dimension getOffsets(NavBarItem item) {
    final Dimension size = new Dimension();
    if (!item.isPopupElement()) {
      JBInsets.addTo(size, getElementPadding(item));
      if (!ExperimentalUI.isNewUI()) {
        size.width += getDecorationOffset() + (item.isFirstElement() ? getFirstElementLeftOffset() : 0);
      }
    }
    return size;
  }

  protected Color getBackgroundColor() {
    return ColorUtil.darker(UIUtil.getPanelBackground(), 1);
  }

  @Override
  public void clearItems() {
    if (!ExperimentalUI.isNewUI()) {
      cache.clear();
    }
  }

  @Override
  public int getPopupOffset(@NotNull NavBarItem item) {
    return navBarPopupOffset(item.isFirstElement());
  }
}
