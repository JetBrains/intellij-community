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
import com.intellij.ui.ColorUtil;
import com.intellij.ui.paint.EffectPainter;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.MouseEventHandler;
import org.intellij.lang.annotations.JdkConstants.FontStyle;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.border.AbstractBorder;

import static javax.swing.SwingUtilities.isLeftMouseButton;

/**
 * @author Sergey.Malenkov
 */
public class Breadcrumbs extends JComponent {
  @SuppressWarnings("FieldCanBeLocal")
  private final MouseHandler containerMouseHandler = new MouseHandler(event -> getCrumb(event.getX(), event.getY()));
  private final MouseHandler componentMouseHandler = new MouseHandler(event -> getCrumb(event.getComponent()));

  private BiConsumer<Crumb, InputEvent> hover = ((crumb, event) -> hovered = crumb);
  private BiConsumer<Crumb, InputEvent> select = ((crumb, event) -> selected = crumb);

  private final Font[] cache = new Font[4];

  private Crumb last;
  private Crumb hovered;
  private Crumb selected;

  public Breadcrumbs() {
    addMouseListener(containerMouseHandler);
    addMouseMotionListener(containerMouseHandler);
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
    if (last == null || isSelected(last)) return false;
    for (Crumb current : getCrumbs()) {
      if (current == crumb) return false;
      if (isSelected(current)) break;
    }
    return true;
  }

  public Iterable<Crumb> getCrumbs() {
    return getComponents(this, Breadcrumbs::getCrumb);
  }

  public void setCrumbs(Iterable<? extends Crumb> crumbs) {
    ArrayList<CrumbLabel> labels = getComponents(this, Breadcrumbs::toCrumbLabel);
    last = null;
    int index = 0;
    if (crumbs != null) {
      for (Crumb crumb : crumbs) {
        if (crumb != null) {
          last = crumb;
          if (index < labels.size()) {
            labels.get(index++).setCrumb(crumb);
          }
          else {
            CrumbLabel label = new CrumbLabel();
            label.setCrumb(crumb);
            label.setBorder(STATELESS_BORDER);
            label.addMouseListener(componentMouseHandler);
            label.addMouseMotionListener(componentMouseHandler);
            add(label);
            index++;
          }
        }
      }
    }
    while (index < labels.size()) {
      labels.get(index++).setCrumb(null);
    }
    select.accept(last, null);
    revalidate();
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    // this custom component does not have a corresponding UI,
    // so we should care of painting its background
    if (isOpaque()) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
    }
    super.paintComponent(g);
  }

  protected void paint(Graphics2D g, int x, int y, int width, int height, Crumb crumb) {
    int scale = getScale(this);
    EffectType type = getEffectType(crumb);
    Color color = getEffectColor(crumb);
    Color background = getBackground(crumb);
    if (color != null && type != null) {
      EffectPainter painter = getEffectPainter(type);
      if (painter != null) {
        if (background != null) {
          g.setColor(background);
          g.fillRect(x + 2 * scale, y + 2 * scale, width - 4 * scale, height - 4 * scale);
        }
        Font font = getFont(crumb);
        if (font != null) {
          FontMetrics metrics = g.getFontMetrics(font);
          if (metrics != null) {
            int descent = metrics.getDescent();
            int baseline = y + height - 2 * scale - descent;
            if (painter == EffectPainter.STRIKE_THROUGH) descent = metrics.getAscent();

            g.setColor(color);
            painter.paint(g, x + 5 * scale, baseline, width - 10 * scale, descent, font);
          }
        }
      }
      else if (type == EffectType.ROUNDED_BOX) {
        RectanglePainter.paint(g, x + scale, y + scale, width - 2 * scale, height - 2 * scale, height / 2, background, color);
      }
      else if (type == EffectType.BOXED) {
        RectanglePainter.paint(g, x + scale, y + scale, width - 2 * scale, height - 2 * scale, 0, background, color);
      }
    }
    else if (background != null) {
      g.setColor(background);
      g.fillRect(x, y, width, height);
      if (isSelected(crumb)) {
        paint(g, x, y, width, height, crumb, scale * 2);
      }
    }
    else if (isSelected(crumb)) {
      paint(g, x, y, width, height, crumb, scale * 2);
    }
    else if (isHovered(crumb)) {
      paint(g, x, y, width, height, crumb, scale);
    }
  }

  protected void paint(Graphics2D g, int x, int y, int width, int height, Crumb crumb, int thickness) {
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
    Color foreground = getForeground(crumb);
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

  private Crumb getCrumb(int x, int y) {
    return getCrumb(getComponentAt(x, y));
  }

  private static Crumb getCrumb(Component component) {
    CrumbLabel label = toCrumbLabel(component);
    return label == null ? null : label.crumb;
  }

  private static CrumbLabel toCrumbLabel(Component component) {
    return component instanceof CrumbLabel ? (CrumbLabel)component : null;
  }

  private static Component toVisible(Component component) {
    return component.isVisible() ? component : null;
  }

  private static Component toVisibleAndValid(Component component) {
    return component.isVisible() && component.isValid() ? component : null;
  }

  private static <T> ArrayList<T> getComponents(Container container, Function<Component, T> function) {
    synchronized (container.getTreeLock()) {
      int count = container.getComponentCount();
      ArrayList<T> components = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        T component = function.apply(container.getComponent(i));
        if (component != null) components.add(component);
      }
      return components;
    }
  }

  private static Dimension getPreferredSize(Iterable<Component> components) {
    Dimension size = new Dimension();
    for (Component component : components) {
      Dimension preferred = component.getPreferredSize();
      size.width += preferred.width;
      if (size.height < preferred.height) {
        size.height = preferred.height;
      }
    }
    return size;
  }

  private static int getPreferredHeight(Component component) {
    Font font = component.getFont();
    if (font == null) return 0;

    FontMetrics metrics = component.getFontMetrics(font);
    int height = metrics != null ? metrics.getHeight() : font.getSize();

    Insets insets = getCrumbInsets(null, getScale(font));
    return insets.top + insets.bottom + height;
  }

  @SuppressWarnings("UseDPIAwareInsets")
  private static Insets getCrumbInsets(Insets insets, int scale) {
    if (insets == null) insets = new Insets(0, 0, 0, 0);
    insets.top += 2 * scale;
    insets.left += 5 * scale;
    insets.right += 5 * scale;
    insets.bottom += 2 * scale;
    return insets;
  }

  private static int getGap(Component component) {
    return 10 * getScale(component);
  }

  private static int getScale(Component component) {
    return component == null ? 1 : getScale(component.getFont());
  }

  private static int getScale(Font font) {
    return font == null ? 1 : getScale(font.getSize());
  }

  private static int getScale(int height) {
    return height <= 10 ? 1 : (height / 10);
  }

  private static final AbstractBorder STATELESS_BORDER = new AbstractBorder() {
    @Override
    public Insets getBorderInsets(Component component, Insets insets) {
      return getCrumbInsets(insets, component == null ? 1 : getScale(component.getParent()));
    }
  };

  private static final AbstractLayoutManager STATELESS_LAYOUT = new AbstractLayoutManager() {
    @Override
    public void invalidateLayout(Container container) {
      getComponents(container, Breadcrumbs::toVisibleAndValid).forEach(Component::invalidate);
    }

    @Override
    public Dimension preferredLayoutSize(Container container) {
      ArrayList<Component> components = getComponents(container, Breadcrumbs::toVisible);
      Dimension size = getPreferredSize(components);
      size.width += components.size() * getGap(container);
      if (size.height == 0) size.height = getPreferredHeight(container);
      JBInsets.addTo(size, container.getInsets());
      return size;
    }

    @Override
    public void layoutContainer(Container container) {
      ArrayList<Component> components = getComponents(container, Breadcrumbs::toVisible);
      if (!components.isEmpty()) {
        Dimension size = getPreferredSize(components);
        Rectangle bounds = new Rectangle(container.getWidth(), container.getHeight());
        JBInsets.removeFrom(bounds, container.getInsets());
        int count = components.size();
        if (count == 1) {
          if (bounds.width > size.width) bounds.width = size.width;
          components.get(0).setBounds(bounds);
        }
        else /*if (bounds.width < size.width) {
            }
            else*/ {
          int gap = getGap(container);
          for (Component component : components) {
            Dimension preferred = component.getPreferredSize();
            component.setBounds(bounds.x, bounds.y, preferred.width, bounds.height);
            bounds.x += preferred.width + gap;
          }
        }
      }
    }
  };

  private final class MouseHandler extends MouseEventHandler {
    private final Function<MouseEvent, Crumb> function;

    MouseHandler(Function<MouseEvent, Crumb> function) {
      this.function = function;
    }

    @Override
    protected void handle(MouseEvent event) {
      if (!event.isConsumed()) {
        Crumb crumb = null;
        BiConsumer<Crumb, InputEvent> consumer = null;
        switch (event.getID()) {
          case MouseEvent.MOUSE_MOVED:
          case MouseEvent.MOUSE_ENTERED:
            crumb = function.apply(event);
          case MouseEvent.MOUSE_EXITED:
            if (!isHovered(crumb)) consumer = hover;
            break;
          case MouseEvent.MOUSE_CLICKED:
            if (!isLeftMouseButton(event)) break;
            crumb = function.apply(event);
            if (crumb != null) consumer = select;
            break;
        }
        if (consumer != null) {
          consumer.accept(crumb, event);
          event.consume();
          repaint();
        }
      }
    }
  }
}
