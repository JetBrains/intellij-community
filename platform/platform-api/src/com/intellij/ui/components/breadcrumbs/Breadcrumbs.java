/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.components.breadcrumbs;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.paint.EffectPainter;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.MouseEventHandler;
import org.intellij.lang.annotations.JdkConstants.FontStyle;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javax.swing.Icon;
import javax.swing.JComponent;

import static com.intellij.ide.ui.AntialiasingType.getKeyForCurrentScope;
import static java.util.stream.Collectors.toList;
import static javax.swing.SwingConstants.LEFT;
import static javax.swing.SwingConstants.RIGHT;
import static javax.swing.SwingConstants.CENTER;
import static javax.swing.SwingUtilities.isLeftMouseButton;
import static javax.swing.SwingUtilities.layoutCompoundLabel;

/**
 * @author Sergey.Malenkov
 */
public class Breadcrumbs extends JComponent {

  private BiConsumer<Crumb, InputEvent> hover = ((crumb, event) -> hovered = crumb);
  private BiConsumer<Crumb, InputEvent> select = ((crumb, event) -> selected = crumb);

  private final ArrayList<CrumbView> views = new ArrayList<>();
  private final Font[] cache = new Font[4];
  private LinearGradientPaint gradient;
  private Crumb hovered;
  private Crumb selected;

  public Breadcrumbs() {
    MouseHandler handler = new MouseHandler();
    addMouseListener(handler);
    addMouseMotionListener(handler);
    setLayout(STATELESS_LAYOUT);
    setOpaque(true);
  }

  public void onHover(BiConsumer<Crumb, InputEvent> consumer) {
    hover = hover.andThen(consumer);
  }

  public void onSelect(BiConsumer<Crumb, InputEvent> consumer) {
    select = select.andThen(consumer);
  }

  public boolean isHovered(Crumb crumb) {
    return hovered == crumb;
  }

  public boolean isSelected(Crumb crumb) {
    return selected == crumb;
  }

  public boolean isAfterSelected(Crumb crumb) {
    for (CrumbView view : views) {
      if (view.crumb != null) {
        if (view.crumb == crumb) return false;
        if (isSelected(view.crumb)) break;
      }
    }
    return true;
  }

  public Crumb getCrumbAt(int x, int y) {
    for (CrumbView view : views) {
      if (view.contains(x, y)) return view.crumb;
    }
    return null;
  }

  public Iterable<Crumb> getCrumbs() {
    return views.stream().map(view -> view.crumb).filter(crumb -> crumb != null).collect(toList());
  }

  public void setCrumbs(Iterable<? extends Crumb> crumbs) {
    CrumbView view = null;
    int index = 0;
    if (crumbs != null) {
      for (Crumb crumb : crumbs) {
        if (crumb != null) {
          if (index < views.size()) {
            view = views.get(index++);
            view.initialize(crumb);
          }
          else {
            view = new CrumbView(view, crumb);
            views.add(view);
            index++;
          }
        }
      }
    }
    while (index < views.size()) {
      views.get(index++).initialize(null);
    }
    select.accept(view == null ? null : view.crumb, null);
    revalidate();
    repaint();
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    return hovered == null ? null : hovered.getTooltip();
  }

  @Override
  protected void paintComponent(Graphics g) {
    // this custom component does not have a corresponding UI,
    // so we should care of painting its background
    if (isOpaque()) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
    }
    if (g instanceof Graphics2D) {
      Graphics2D g2d = (Graphics2D)g;
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, getKeyForCurrentScope(!Registry.is("editor.breadcrumbs.system.font")));
      for (CrumbView view : views) {
        if (view.crumb != null) view.paint(g2d);
      }
    }
  }

  protected void paintMarker(Graphics2D g, int x, int y, int width, int height, Crumb crumb, int thickness) {
    if (thickness > 0) {
      Color foreground = getMarkerForeground(crumb);
      if (foreground != null) {
        g.setColor(foreground);
        g.fillRect(x, y + height - thickness, width, thickness);
      }
    }
  }

  protected Color getMarkerForeground(Crumb crumb) {
    if (!Registry.is("editor.breadcrumbs.marker")) return null;
    CrumbView found = getCrumbView(view -> view.crumb == crumb);
    Color foreground = found == null ? null : found.foreground;
    if (foreground == null) return null;
    double alpha = Registry.doubleValue("editor.breadcrumbs.marker.alpha");
    return ColorUtil.toAlpha(foreground, (int)(alpha * foreground.getAlpha()));
  }

  protected Font getFont(Crumb crumb) {
    Font font = getFont();
    if (font == null) return null;

    int old = font.getStyle();
    if (font != cache[old]) {
      for (int i = 0; i < cache.length; i++) {
        cache[i] = i == old ? font : null;
      }
    }
    int style = getFontStyle(crumb);
    if (style == old) return font;

    Font cached = cache[style];
    if (cached != null) return cached;

    font = font.deriveFont(style);
    cache[style] = font;
    return font;
  }

  @FontStyle
  protected int getFontStyle(Crumb crumb) {
    TextAttributes attributes = getAttributes(crumb);
    return attributes == null ? Font.PLAIN : attributes.getFontType();
  }

  protected Color getForeground(Crumb crumb) {
    TextAttributes attributes = getAttributes(crumb);
    Color foreground = attributes == null ? null : attributes.getForegroundColor();
    return foreground != null ? foreground : getForeground();
  }

  protected Color getBackground(Crumb crumb) {
    TextAttributes attributes = getAttributes(crumb);
    return attributes == null ? null : attributes.getBackgroundColor();
  }

  protected Color getEffectColor(Crumb crumb) {
    TextAttributes attributes = getAttributes(crumb);
    return attributes == null ? null : attributes.getEffectColor();
  }

  protected EffectType getEffectType(Crumb crumb) {
    TextAttributes attributes = getAttributes(crumb);
    return attributes == null ? null : attributes.getEffectType();
  }

  protected EffectPainter getEffectPainter(EffectType type) {
    if (type == EffectType.STRIKEOUT) return EffectPainter.STRIKE_THROUGH;
    if (type == EffectType.WAVE_UNDERSCORE) return EffectPainter.WAVE_UNDERSCORE;
    if (type == EffectType.LINE_UNDERSCORE) return EffectPainter.LINE_UNDERSCORE;
    if (type == EffectType.BOLD_LINE_UNDERSCORE) return EffectPainter.BOLD_LINE_UNDERSCORE;
    if (type == EffectType.BOLD_DOTTED_LINE) return EffectPainter.BOLD_DOTTED_UNDERSCORE;
    return null;
  }

  protected TextAttributes getAttributes(Crumb crumb) {
    return null;
  }

  private CrumbView getCrumbView(Predicate<CrumbView> predicate) {
    for (CrumbView view : views) if (view.crumb != null && predicate.test(view)) return view;
    return null;
  }

  private void layout(boolean update) {
    Rectangle bounds = new Rectangle(getWidth(), getHeight());
    JBInsets.removeFrom(bounds, getInsets());
    int scale = getScale();
    for (CrumbView view : views) {
      if (view.crumb != null) {
        if (update || view.font == null) view.update();
        bounds.x += getGap(view, scale);
        view.setBounds(bounds.x, bounds.y, view.preferred.width, bounds.height, scale);
        bounds.x += view.preferred.width;
      }
    }
    if (Registry.is("editor.breadcrumbs.marker")) {
      gradient = null;
    }
    else {
      Color color = getForeground();
      Color alpha = ColorUtil.toAlpha(color, 0);
      Color[] colors = {alpha, color, alpha};
      float[] floats = {.2f, .5f, .8f};
      gradient = new LinearGradientPaint(0, bounds.y, 0, bounds.y + bounds.height, floats, colors);
    }
  }

  private void updatePreferredSize(Dimension size, int scale) {
    for (CrumbView view : views) {
      if (view.crumb != null) {
        if (view.font == null) view.update();
        size.width += view.preferred.width + getGap(view, scale);
        if (size.height < view.preferred.height) {
          size.height = view.preferred.height;
        }
      }
    }
    if (size.width > 0) {
      size.width += getGap(null, scale);
    }
    if (size.height == 0) {
      Font font = getFont();
      if (font != null) {
        FontMetrics fm = getFontMetrics(font);
        size.height = 2 * getTopBottom(scale) + (fm != null ? fm.getHeight() : font.getSize());
      }
    }
  }

  private static int getGap(CrumbView view, int scale) {
    if (Registry.is("editor.breadcrumbs.marker")) {
      return view != null && view.parent != null ? 10 * scale : 0;
    }
    int gap = Registry.intValue("editor.breadcrumbs.gap.right", 9);
    if (view != null) {
      int left = Registry.intValue("editor.breadcrumbs.gap.left", 5);
      gap = view.parent != null ? gap + left : left;
    }
    return gap * scale;
  }

  private static int getLeftRight(int scale) {
    return 5 * scale;
  }

  private static int getTopBottom(int scale) {
    return 2 * scale;
  }

  private int getScale() {
    Font font = getFont();
    if (font != null) {
      int size = font.getSize();
      if (size > 10) return size / 10;
    }
    return 1;
  }

  private static final AbstractLayoutManager STATELESS_LAYOUT = new AbstractLayoutManager() {
    @Override
    public Dimension preferredLayoutSize(Container container) {
      Dimension size = new Dimension();
      if (container instanceof Breadcrumbs) {
        Breadcrumbs breadcrumbs = (Breadcrumbs)container;
        breadcrumbs.updatePreferredSize(size, breadcrumbs.getScale());
      }
      JBInsets.addTo(size, container.getInsets());
      return size;
    }

    @Override
    public void layoutContainer(Container container) {
      if (container instanceof Breadcrumbs) {
        Breadcrumbs breadcrumbs = (Breadcrumbs)container;
        breadcrumbs.layout(false);
      }
    }
  };

  private final class MouseHandler extends MouseEventHandler {
    @Override
    protected void handle(MouseEvent event) {
      if (!event.isConsumed()) {
        Crumb crumb = null;
        BiConsumer<Crumb, InputEvent> consumer = null;
        switch (event.getID()) {
          case MouseEvent.MOUSE_MOVED:
          case MouseEvent.MOUSE_ENTERED:
            crumb = getCrumbAt(event.getX(), event.getY());
          case MouseEvent.MOUSE_EXITED:
            if (!isHovered(crumb)) consumer = hover;
            break;
          case MouseEvent.MOUSE_CLICKED:
            if (!isLeftMouseButton(event)) break;
            crumb = getCrumbAt(event.getX(), event.getY());
            if (crumb != null) consumer = select;
            break;
        }
        if (consumer != null) {
          consumer.accept(crumb, event);
          event.consume();
          layout(true);
          repaint();
        }
      }
    }
  }

  private final class CrumbView {
    private final Rectangle bounds = new Rectangle();
    private final Dimension preferred = new Dimension();

    private final CrumbView parent;
    private Crumb crumb;
    private Icon icon;
    private String text;
    private Path2D path;

    private int scale;
    private Font font;
    private Color foreground;
    private Color background;
    private Color effectColor;
    private EffectType effectType;

    CrumbView(CrumbView parent, Crumb crumb) {
      this.parent = parent;
      initialize(crumb);
    }

    void initialize(Crumb crumb) {
      this.crumb = crumb;
      icon = crumb == null ? null : crumb.getIcon();
      text = crumb == null ? null : crumb.getText();
      path = null;
      font = null;
    }

    private void update() {
      foreground = getForeground(crumb);
      background = getBackground(crumb);
      effectType = getEffectType(crumb);
      effectColor = getEffectColor(crumb);

      Font font = getFont(crumb);
      if (font == null) font = getFont();

      int scale = getScale();
      if (this.scale != scale && icon != null) {
        icon = IconUtil.scale(icon, Breadcrumbs.this, scale);
      }
      if (this.scale != scale || this.font != font) {
        this.scale = scale;
        this.font = font;

        preferred.width = 2 * getLeftRight(scale);
        preferred.height = 2 * getTopBottom(scale);

        if (font != null && !StringUtil.isEmpty(text)) {
          FontMetrics fm = getFontMetrics(font);
          Rectangle iconR = new Rectangle();
          Rectangle textR = new Rectangle();
          layout(fm, iconR, textR, new Rectangle(Short.MAX_VALUE, Short.MAX_VALUE));
          preferred.width += Math.max(iconR.x + iconR.width, textR.x + textR.width) - Math.min(iconR.x, textR.x);
          preferred.height += Math.max(iconR.y + iconR.height, textR.y + textR.height) - Math.min(iconR.y, textR.y);
        }
        else if (icon != null) {
          preferred.width += icon.getIconWidth();
          preferred.height += icon.getIconHeight();
        }
      }
    }

    private String layout(FontMetrics fm, Rectangle iconR, Rectangle textR, Rectangle viewR) {
      int gap = icon == null ? 0 : icon.getIconWidth() / 4;
      return layoutCompoundLabel(fm, text, icon, CENTER, LEFT, CENTER, RIGHT, viewR, iconR, textR, gap);
    }

    private Rectangle getBounds(int dx, int dy) {
      return new Rectangle(bounds.x + dx, bounds.y + dy, bounds.width - dx - dx, bounds.height - dy - dy);
    }

    private void setBounds(int x, int y, int width, int height, int scale) {
      bounds.setBounds(x, y, width, height);
      if (Registry.is("editor.breadcrumbs.marker")) {
        path = null;
      }
      else {
        int left = getGap(this, scale);
        int right = getGap(null, scale);
        path = new Path2D.Double();
        path.moveTo(x - left, y);
        path.lineTo(x + width, y);
        path.lineTo(x + width + right, y + height * 0.5);
        path.lineTo(x + width, y + height);
        path.lineTo(x - left, y + height);
        if (parent != null) path.lineTo(x - left + right, y + height * 0.5);
        path.closePath();
      }
    }

    private boolean contains(int x, int y) {
      return crumb != null && (path != null ? path.contains(x, y) : bounds.contains(x, y));
    }

    private void paint(Graphics2D g) {
      int scale = getScale();
      if (path != null) {
        if (background != null) {
          g.setColor(background);
          g.fill(path);
        }
        if (parent != null && parent.background == background && gradient != null) {
          int left = getGap(this, scale);
          int right = getGap(null, scale);
          Path2D path = new Path2D.Double();
          path.moveTo(bounds.x - left, bounds.y);
          path.lineTo(bounds.x - left + right, bounds.y + bounds.height * 0.5);
          path.lineTo(bounds.x - left, bounds.y + bounds.height);
          g.setPaint(gradient);
          g.draw(path); //TODO: paint >
        }
      }
      if (effectType == EffectType.ROUNDED_BOX) {
        Rectangle bounds = getBounds(scale, scale);
        RectanglePainter.paint(g, bounds.x, bounds.y, bounds.width, bounds.height, bounds.height / 2, background, effectColor);
      }
      else if (effectType == EffectType.BOXED) {
        Rectangle bounds = getBounds(scale, scale);
        RectanglePainter.paint(g, bounds.x, bounds.y, bounds.width, bounds.height, 0, background, effectColor);
      }
      else if (background != null) {
        g.setColor(background);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        if (isSelected(crumb)) {
          paintMarker(g, bounds.x, bounds.y, bounds.width, bounds.height, crumb, scale * 2);
        }
      }
      else if (isSelected(crumb)) {
        paintMarker(g, bounds.x, bounds.y, bounds.width, bounds.height, crumb, scale * 2);
      }
      else if (isHovered(crumb)) {
        paintMarker(g, bounds.x, bounds.y, bounds.width, bounds.height, crumb, scale);
      }
      if (font != null) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics(font);
        if (fm != null) {
          Rectangle iconR = new Rectangle();
          Rectangle textR = new Rectangle();
          Rectangle viewR = getBounds(getLeftRight(scale), getTopBottom(scale));
          String text = layout(fm, iconR, textR, viewR);
          if (!StringUtil.isEmpty(text)) {
            g.setColor(foreground != null ? foreground : getForeground());
            g.drawString(text, textR.x, textR.y += fm.getAscent());
            if (effectColor != null && effectType != null) {
              EffectPainter painter = getEffectPainter(effectType);
              if (painter != null) {
                g.setColor(effectColor);
                textR.height = painter == EffectPainter.STRIKE_THROUGH ? fm.getAscent() : fm.getDescent();
                painter.paint(g, textR.x, textR.y, textR.width, textR.height, font);
              }
            }
          }
          if (icon != null) {
            icon.paintIcon(Breadcrumbs.this, g, iconR.x, iconR.y);
          }
        }
      }
    }
  }
}
