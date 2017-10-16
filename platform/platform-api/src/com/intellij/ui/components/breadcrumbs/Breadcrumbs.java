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
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.paint.EffectPainter;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MouseEventHandler;
import org.intellij.lang.annotations.JdkConstants.FontStyle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static com.intellij.ide.ui.AntialiasingType.getKeyForCurrentScope;
import static com.intellij.util.ui.UIUtil.DEF_SYSTEM_FONT_SIZE;
import static java.util.stream.Collectors.toList;
import static javax.swing.SwingConstants.*;
import static javax.swing.SwingUtilities.isLeftMouseButton;
import static javax.swing.SwingUtilities.layoutCompoundLabel;

/**
 * @author Sergey.Malenkov
 */
public class Breadcrumbs extends JBPanelWithEmptyText {
  private static final int LEFT_RIGHT = 5;
  private static final int TOP_BOTTOM = 3;

  private BiConsumer<Crumb, InputEvent> hover = ((crumb, event) -> hovered = crumb);
  private BiConsumer<Crumb, InputEvent> select = ((crumb, event) -> selected = crumb);

  private final ArrayList<CrumbView> views = new ArrayList<>();
  private final Font[] cache = new Font[4];
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
    if (hovered != null) hover.accept(null, null);
    if (selected != null) select.accept(null, null);
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
        view.setBounds(bounds.x, bounds.y, view.preferred.width, bounds.height, scale);
        bounds.x += view.preferred.width;
      }
    }
  }

  private void updatePreferredSize(Dimension size, int scale) {
    for (CrumbView view : views) {
      if (view.crumb != null) {
        if (view.font == null) view.update();
        size.width += view.preferred.width;
        if (size.height < view.preferred.height) {
          size.height = view.preferred.height;
        }
      }
    }
    if (size.height == 0) {
      Font font = getFont();
      if (font != null) {
        FontMetrics fm = getFontMetrics(font);
        size.height = scale * (TOP_BOTTOM + TOP_BOTTOM) + (fm != null ? fm.getHeight() : font.getSize());
      }
    }
  }

  private int getScale() {
    Font font = getFont();
    if (font != null) {
      int size = font.getSize();
      if (size > 10) return size / 10;
    }
    return 1;
  }

  private static float getFontSize(Font font) {
    return font == null ? DEF_SYSTEM_FONT_SIZE : font.getSize2D();
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
      icon = null;
      text = null;
      path = null;
      font = null;
      foreground = null;
      background = null;
      effectColor = null;
    }

    private void update() {
      icon = crumb.getIcon();
      text = crumb.getText();
      font = getFont(crumb);
      foreground = getForeground(crumb);
      background = getBackground(crumb);
      effectType = getEffectType(crumb);
      effectColor = getEffectColor(crumb);

      // use shared foreground and font if not set
      if (foreground == null) foreground = getForeground();
      if (font == null) font = getFont();

      // scale loaded icon by font
      if (icon != null) icon = IconUtil.scaleByFont(icon, Breadcrumbs.this, getFontSize(font));

      // calculate preferred size
      int scale = getScale();
      preferred.width = scale * (LEFT_RIGHT + LEFT_RIGHT + getLeftGap() + getRightGap());
      preferred.height = scale * (TOP_BOTTOM + TOP_BOTTOM);

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

    private String layout(FontMetrics fm, Rectangle iconR, Rectangle textR, Rectangle viewR) {
      int gap = icon == null ? 0 : icon.getIconWidth() / 4;
      return layoutCompoundLabel(fm, text, icon, CENTER, LEFT, CENTER, RIGHT, viewR, iconR, textR, gap);
    }

    private Rectangle getBounds(int dx, int dy) {
      return new Rectangle(bounds.x + dx, bounds.y + dy, bounds.width - dx - dx, bounds.height - dy - dy);
    }

    private void setBounds(int x, int y, int width, int height, int scale) {
      int left = scale * getLeftGap();
      int right = scale * getRightGap();
      bounds.setBounds(x + left, y, width - left - right, height);
      path = Registry.is("editor.breadcrumbs.marker") ? null : createPath(scale, true);
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
        if (parent != null && parent.background == background && !Registry.is("editor.breadcrumbs.marker")) {
          Graphics2D g2 = (Graphics2D)g.create();
          float stroke = JBUI.getFontScale(getFontSize(getFont()));
          // calculate a visible width of separator (30% of a whole path)
          int delta = (int)(scale * (.3 * getRightGap() + getLeftGap()));
          g2.clipRect(bounds.x - delta, bounds.y, Short.MAX_VALUE, bounds.height);
          g2.setPaint(getForeground());
          if (stroke > 1) g2.setStroke(new BasicStroke(stroke));
          g2.draw(createPath(scale, false));
          g2.dispose();
        }
      }
      if (effectType == EffectType.ROUNDED_BOX && effectColor != null) {
        Rectangle bounds = getBounds(scale, scale);
        RectanglePainter.paint(g, bounds.x, bounds.y, bounds.width, bounds.height, bounds.height / 2, background, effectColor);
      }
      else if (effectType == EffectType.BOXED && effectColor != null) {
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
      if (font != null && foreground != null) {
        g.setFont(font);
        FontMetrics fm = getFontMetrics(font);
        if (fm != null) {
          Rectangle iconR = new Rectangle();
          Rectangle textR = new Rectangle();
          Rectangle viewR = getBounds(scale * LEFT_RIGHT, scale * TOP_BOTTOM);
          String text = layout(fm, iconR, textR, viewR);
          if (!StringUtil.isEmpty(text)) {
            g.setColor(foreground);
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

    private int getLeftGap() {
      return !Registry.is("editor.breadcrumbs.marker")
             ? Registry.intValue("editor.breadcrumbs.gap.left", 5)
             : parent != null ? 10 : 0;
    }

    private int getRightGap() {
      return !Registry.is("editor.breadcrumbs.marker")
             ? Registry.intValue("editor.breadcrumbs.gap.right", 9)
             : 0;
    }

    private Path2D createPath(int scale, boolean closed) {
      Path2D path = new Path2D.Double();

      int left = scale * getLeftGap();
      int right = scale * getRightGap();
      if (parent != null) {
        left += right;
        path.moveTo(bounds.x - left, bounds.y);
        path.lineTo(bounds.x - left + right, bounds.y + bounds.height * 0.5);
      }
      else {
        path.moveTo(bounds.x - left, bounds.y);
      }
      path.lineTo(bounds.x - left, bounds.y + bounds.height);
      if (closed) {
        path.lineTo(bounds.x + bounds.width, bounds.y + bounds.height);
        path.lineTo(bounds.x + bounds.width + right, bounds.y + bounds.height * 0.5);
        path.lineTo(bounds.x + bounds.width, bounds.y);
        path.closePath();
      }
      return path;
    }
  }
}
