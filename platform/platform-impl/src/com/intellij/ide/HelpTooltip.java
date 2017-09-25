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
package com.intellij.ide;

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.Alarm;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.beans.PropertyChangeListener;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class HelpTooltip {
  private static Color BACKGROUND_COLOR = new JBColor(Gray.xF7, new Color(0x46484a));
  private static Color FONT_COLOR = new JBColor(Gray.x33, Gray.xBF);
  private static Color SHORTCUT_COLOR = new JBColor(Gray.x78, Gray.x87);
  private static Color BORDER_COLOR = new JBColor(Gray.xA1, new Color(0x5b5c5e));

  private static Border DEFAULT_BORDER = JBUI.Borders.empty(10, 10, 10, 16);
  private static Border SMALL_BORDER = JBUI.Borders.empty(SystemInfo.isMac ? 4 : 5, 8, 4, 8);

  private static final int DEFAULT_OFFSET = 2;

  private static final int VGAP = JBUI.scale(UIUtil.DEFAULT_VGAP);
  private static final int HGAP = JBUI.scale(UIUtil.DEFAULT_HGAP);
  private static final int MAX_WIDTH = JBUI.scale(250);

  private static final String DOTS = "...";
  private static final String PARAGRAPH_SPLITTER = "<p/?>";

  private String title;
  private String shortcut;
  private String description;
  private LinkLabel link;
  private boolean neverHide;
  private Alignment alignment = Alignment.BOTTOM;

  private JComponent owner;
  private ComponentPopupBuilder myPopupBuilder;
  private JBPopup myPopup;
  private Alarm popupAlarm = new Alarm();
  private boolean isOverPopup;
  private boolean isMultiline;
  private int myDismissDelay;

  private MouseAdapter myMouseListener;
  private PropertyChangeListener myPropertyChangeListener;

  public enum Alignment {
    RIGHT {
      @Override public Point getPoint(Dimension ownerSize, Dimension popupSize) {
        int offset = getOffset();
        return new Point(ownerSize.width + VGAP - offset, offset);
      }
    },

    BOTTOM {
      @Override public Point getPoint(Dimension ownerSize, Dimension popupSize) {
        int offset = getOffset();
        return new Point(offset, ownerSize.height + VGAP - offset);
      }
    },

    TOP {
      @Override public Point getPoint(Dimension ownerSize, Dimension popupSize) {
        int offset = getOffset();
        return new Point(offset, - popupSize.height - VGAP + offset);
      }
    },

    HELP_BUTTON {
      @Override public Point getPoint(Dimension ownerSize, Dimension popupSize) {
        int offset = getOffset();
        return new Point(-offset, ownerSize.height + VGAP - offset);
      }
    };

    private final int myOffset;

    Alignment() {
      int offset = UIManager.getInt("HelpTooltip.offset");
      myOffset = (offset == 0) ? DEFAULT_OFFSET : offset;
    }

    protected int getOffset() {
      return JBUI.scale(myOffset);
    }

    public abstract Point getPoint(Dimension ownerSize, Dimension popupSize);
  }

  @SuppressWarnings("unused")
  public HelpTooltip setTitle(String title) {
    this.title = title;
    return this;
  }

  @SuppressWarnings("unused")
  public HelpTooltip setShortcut(String shortcut) {
    this.shortcut = shortcut;
    return this;
  }

  @SuppressWarnings("unused")
  public HelpTooltip setDescription(String description) {
    this.description = description;
    return this;
  }

  @SuppressWarnings("unused")
  public HelpTooltip setLink(String linkText, Runnable linkAction) {
    this.link = LinkLabel.create(linkText, () -> {
      hidePopup(true);
      linkAction.run();
    });
    return this;
  }

  @SuppressWarnings("unused")
  public HelpTooltip setNeverHideOnTimeout(boolean neverHide) {
    this.neverHide = neverHide;
    return this;
  }

  @SuppressWarnings("unused")
  public HelpTooltip setLocation(Alignment alignment) {
    this.alignment = alignment;
    return this;
  }

  public void installOn(JComponent component) {
    JPanel tipPanel = new JPanel();
    tipPanel.addMouseListener(new MouseAdapter() {
      @Override public void mouseEntered(MouseEvent e) {
        isOverPopup = true;
      }

      @Override public void mouseExited(MouseEvent e) {
        if (link == null || !link.getBounds().contains(e.getPoint())) {
          isOverPopup = false;
          hidePopup(false);
        }
      }
    });

    tipPanel.setLayout(new VerticalLayout(VGAP));
    tipPanel.setBackground(BACKGROUND_COLOR);

    if (StringUtil.isNotEmpty(title)) {
      tipPanel.add(new Header(), VerticalLayout.TOP);
    }

    if (StringUtil.isNotEmpty(description)) {
      String[] pa = description.split(PARAGRAPH_SPLITTER);
      for (String p : pa) {
        if (!p.isEmpty()) {
          tipPanel.add(new Paragraph(p), VerticalLayout.TOP);
        }
      }
    }

    if (link != null) {
      tipPanel.add(link, VerticalLayout.TOP);
    }

    isMultiline = isMultiline || StringUtil.isNotEmpty(description) && (StringUtil.isNotEmpty(title) || link != null);
    tipPanel.setBorder(isMultiline ? DEFAULT_BORDER : SMALL_BORDER);

    myDismissDelay = Registry.intValue(isMultiline ? "ide.helptooltip.full.dismissDelay" : "ide.helptooltip.regular.dismissDelay");
    neverHide = neverHide || DarculaButtonUI.isHelpButton(component);

    owner = component;
    myPopupBuilder = JBPopupFactory.getInstance().
      createComponentPopupBuilder(tipPanel, null).
      setBorderColor(BORDER_COLOR).setShowShadow(false);

    myMouseListener = new MouseAdapter() {
      @Override public void mouseEntered(MouseEvent e) {
        if (myPopup != null && !myPopup.isDisposed()){
          myPopup.cancel();
        }
        scheduleShow(Registry.intValue("ide.tooltip.initialReshowDelay"));
      }

      @Override public void mouseExited(MouseEvent e) {
        scheduleHide(link == null, Registry.intValue("ide.tooltip.initialDelay.highlighter"));
      }

      @Override public void mouseMoved(MouseEvent e) {
        if (myPopup == null || myPopup.isDisposed()) {
          scheduleShow(Registry.intValue("ide.tooltip.reshowDelay"));
        }
      }
    };

    myPropertyChangeListener = evt -> {
      if (evt.getNewValue() == null) { // owner is removed from the component tree
        hidePopup(true);
        if (owner != null) {
          owner.removeMouseListener(myMouseListener);
          owner.removeMouseMotionListener(myMouseListener);
          owner.removePropertyChangeListener(myPropertyChangeListener);
          owner = null;
        }
      }
    };

    owner.addMouseListener(myMouseListener);
    owner.addMouseMotionListener(myMouseListener);
    owner.addPropertyChangeListener("ancestor", myPropertyChangeListener);
  }

  private void scheduleShow(int delay) {
    popupAlarm.cancelAllRequests();
    popupAlarm.addRequest(() -> {
      myPopup = myPopupBuilder.createPopup();
      myPopup.show(new RelativePoint(owner, alignment.getPoint(owner.getSize(), myPopup.getSize())));
      if (!neverHide) {
        scheduleHide(true, myDismissDelay);
      }
    }, delay);
  }

  private void scheduleHide(boolean force, int delay) {
    popupAlarm.cancelAllRequests();
    popupAlarm.addRequest(() -> hidePopup(force), delay);
  }

  private void hidePopup(boolean force) {
    popupAlarm.cancelAllRequests();
    if (myPopup != null && myPopup.isVisible() && (!isOverPopup || force)) {
      myPopup.cancel();
      myPopup = null;
    }
  }

  private class Header extends JPanel {
    private final AttributedString titleString;
    private final AttributedString dotString;
    private final AttributedString shortcutString;

    private LineBreakMeasurer lineMeasurer;
    private TextLayout dotLayout;
    private TextLayout shortcutLayout;

    private final int paragraphStart;
    private final int paragraphEnd;

    private Header() {
      setOpaque(false);

      Font font = getFont();
      Font titleFont = StringUtil.isNotEmpty(description) ? font.deriveFont(Font.BOLD) : font;
      Map<TextAttribute,?> tfa = titleFont.getAttributes();
      titleString = new AttributedString(title, tfa);
      dotString = new AttributedString(DOTS, tfa);
      shortcutString = StringUtil.isNotEmpty(shortcut) ? new AttributedString(shortcut, font.getAttributes()) : null;

      AttributedCharacterIterator paragraph = titleString.getIterator();
      paragraphStart = paragraph.getBeginIndex();
      paragraphEnd = paragraph.getEndIndex();

      // Compute preferred size
      FontMetrics tfm = getFontMetrics(titleFont);
      int titleWidth = SwingUtilities2.stringWidth(this, tfm, title);

      FontMetrics fm = getFontMetrics(font);
      titleWidth += StringUtil.isNotEmpty(shortcut) ? HGAP + SwingUtilities2.stringWidth(this, fm, shortcut) : 0;

      boolean limitWidth = StringUtil.isNotEmpty(description) || link != null;
      isMultiline = limitWidth && (titleWidth > MAX_WIDTH);
      setPreferredSize(isMultiline ? new Dimension(MAX_WIDTH, tfm.getHeight() * 2) : new Dimension(titleWidth, fm.getHeight()));
    }

    @Override public void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D)g.create();
      try {
        g2.setColor(FONT_COLOR);
        GraphicsUtil.setupAntialiasing(g2);
        if (lineMeasurer == null) {
          FontRenderContext frc = g2.getFontRenderContext();
          lineMeasurer = new LineBreakMeasurer(titleString.getIterator(), frc);

          LineBreakMeasurer dotMeasurer = new LineBreakMeasurer(dotString.getIterator(), frc);
          dotLayout = dotMeasurer.nextLayout(Float.POSITIVE_INFINITY);

          if (shortcutString != null) {
            LineBreakMeasurer shortcutMeasurer = new LineBreakMeasurer(shortcutString.getIterator(), frc);
            shortcutLayout = shortcutMeasurer.nextLayout(Float.POSITIVE_INFINITY);
          }
        }

        lineMeasurer.setPosition(paragraphStart);

        float breakWidth = getWidth();
        float drawPosY = 0;
        int line = 0;

        TextLayout layout = null;
        while (lineMeasurer.getPosition() < paragraphEnd && line < 1) {
          layout = lineMeasurer.nextLayout(breakWidth);

          drawPosY += layout.getAscent();
          layout.draw(g2, 0, drawPosY);

          drawPosY += layout.getDescent() + layout.getLeading();
          line++;
        }

        if (lineMeasurer.getPosition() < paragraphEnd) {
          if (shortcutString != null) {
            breakWidth -= dotLayout.getAdvance() + HGAP + shortcutLayout.getAdvance();
          }

          layout = lineMeasurer.nextLayout(breakWidth);

          drawPosY += layout.getAscent();
          layout.draw(g2, 0, drawPosY);

          if (shortcutString != null) {
            dotLayout.draw(g2, layout.getAdvance(), drawPosY);

            g2.setColor(SHORTCUT_COLOR);
            shortcutLayout.draw(g2, layout.getAdvance() + dotLayout.getAdvance() + HGAP, drawPosY);
          }
        } else if (layout != null && shortcutString != null) {
          g2.setColor(SHORTCUT_COLOR);
          if (Float.compare(getWidth() - layout.getAdvance(), shortcutLayout.getAdvance() + HGAP) >= 0) {
            drawPosY = shortcutLayout.getAscent();
            shortcutLayout.draw(g2, layout.getAdvance() + HGAP, drawPosY);
          } else {
            drawPosY += shortcutLayout.getAscent();
            shortcutLayout.draw(g2, 0, drawPosY);
          }
        }
      } finally {
        g2.dispose();
      }
    }
  }

  private class Paragraph extends JLabel {
    private Paragraph(String text) {
      init(text);
    }

    private void init(String text) {
      setForeground(FONT_COLOR);

      View v = BasicHTML.createHTMLView(this, String.format("<html>%s</html>", text));
      float width = v.getPreferredSpan(View.X_AXIS);
      isMultiline = isMultiline || width > MAX_WIDTH;
      setText(width > MAX_WIDTH ?
              String.format("<html><div width=%d>%s</div></html>", MAX_WIDTH, text) :
              String.format("<html>%s</html>", text));

      if (width > MAX_WIDTH) {
        v = (View)getClientProperty(BasicHTML.propertyKey);
        if (v != null) {
          width = 0.0f;
          for(View row : getRows(v)) {
            float rWidth = row.getPreferredSpan(View.X_AXIS);
            if (width < rWidth) {
              width = rWidth;
            }
          }

          v.setSize(width, v.getPreferredSpan(View.Y_AXIS));
        }
      }
    }

    private Collection<View> getRows(@NotNull View root) {
      Collection<View> rows = new ArrayList<>();
      visit(root, rows);
      return rows;
    }

    private void visit(@NotNull View v, Collection<View> result) {
      String cname = v.getClass().getCanonicalName();
      if (cname != null && cname.contains("ParagraphView.Row")) {
        result.add(v);
      }

      for(int i = 0; i < v.getViewCount(); i++) {
        visit(v.getView(i), result);
      }
    }
  }
}
