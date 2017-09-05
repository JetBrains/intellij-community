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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import sun.swing.SwingUtilities2;

import javax.swing.*;
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
import java.util.Map;

public class HelpTooltip {
  private static Color BACKGROUND_COLOR = new JBColor(Gray.xF7, new Color(0x46484a));
  private static Color FONT_COLOR = new JBColor(Gray.x33, Gray.xBF);
  private static Color SHORTCUT_COLOR = new JBColor(Gray.x78, Gray.x87);
  private static Color BORDER_COLOR = new JBColor(Gray.xA1, new Color(0x5b5c5e));

  private static final int SPACE = JBUI.scale(10);
  private static final int MAX_WIDTH = JBUI.scale(250);

  private static final String DOTS = "...";

  private String title;
  private String shortcut;
  private String description;
  private LinkLabel link;
  private boolean neverHide;

  private JComponent owner;
  private ComponentPopupBuilder myPopupBuilder;
  private JBPopup myPopup;
  private Alarm popupAlarm = new Alarm();
  private boolean isOverPopup;
  private boolean isMultiline;
  private int myDismissDelay;

  private MouseAdapter myMouseListener;
  private PropertyChangeListener myPropertyChangeListener;

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

    tipPanel.setLayout(new VerticalLayout(SPACE));
    tipPanel.setBackground(BACKGROUND_COLOR);

    if (StringUtil.isNotEmpty(title)) {
      tipPanel.add(new Header(), VerticalLayout.TOP);
    }

    if (StringUtil.isNotEmpty(description)) {
      String[] pa = description.split("\n");
      for (String p : pa) {
        JLabel label = new JLabel();
        label.setForeground(FONT_COLOR);
        int width = SwingUtilities2.stringWidth(label, label.getFontMetrics(label.getFont()), p);
        isMultiline = isMultiline || width > MAX_WIDTH;
        width = Math.min(MAX_WIDTH, width);
        label.setText(String.format("<html><div width=%d>%s</div></html>", width, p));
        tipPanel.add(label, VerticalLayout.TOP);
      }
    }

    if (link != null) {
      tipPanel.add(link, VerticalLayout.TOP);
    }

    isMultiline = isMultiline || StringUtil.isNotEmpty(description) && (StringUtil.isNotEmpty(title) || link != null);
    tipPanel.setBorder(isMultiline ? JBUI.Borders.empty(10, 10, 10, 16) : JBUI.Borders.empty(5, 8, 4, 8));

    myDismissDelay = Registry.intValue(isMultiline ? "ide.helptooltip.full.dismissDelay" : "ide.helptooltip.regular.dismissDelay");
    neverHide = neverHide || DarculaButtonUI.isHelpButton(component);

    owner = component;
    myPopupBuilder = JBPopupFactory.getInstance().
      createComponentPopupBuilder(tipPanel, null).
      setBorderColor(BORDER_COLOR);

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
      Dimension size = owner.getSize();
      myPopup = myPopupBuilder.createPopup();
      myPopup.show(new RelativePoint(owner, new Point(size.width / 2, size.height + JBUI.scale(4))));
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
      titleWidth += StringUtil.isNotEmpty(shortcut) ? SPACE + SwingUtilities2.stringWidth(this, fm, shortcut) : 0;
      isMultiline = titleWidth > MAX_WIDTH;
      setPreferredSize(isMultiline ? new Dimension(MAX_WIDTH, tfm.getHeight() * 2) : new Dimension(titleWidth, fm.getHeight()));
    }

    @Override public void paintComponent(Graphics g) {
      super.paintComponent(g);

      Graphics2D g2 = (Graphics2D)g.create();
      try {
        g2.setColor(FONT_COLOR);
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
            breakWidth -= dotLayout.getAdvance() + SPACE + shortcutLayout.getAdvance();
          }

          layout = lineMeasurer.nextLayout(breakWidth);

          drawPosY += layout.getAscent();
          layout.draw(g2, 0, drawPosY);

          if (shortcutString != null) {
            dotLayout.draw(g2, layout.getAdvance(), drawPosY);

            g2.setColor(SHORTCUT_COLOR);
            shortcutLayout.draw(g2, layout.getAdvance() + dotLayout.getAdvance() + SPACE, drawPosY);
          }
        } else if (layout != null && shortcutString != null) {
          g2.setColor(SHORTCUT_COLOR);
          if (Float.compare(getWidth() - layout.getAdvance(), shortcutLayout.getAdvance() + SPACE) >= 0) {
            drawPosY = shortcutLayout.getAscent();
            shortcutLayout.draw(g2, layout.getAdvance() + SPACE, drawPosY);
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
}
