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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
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
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Standard implementation of help context tooltip.
 *
 * <h2>Overview</h2>
 * <p>UI design requires to have tooltips that contain detailed information about UI actions and controls.
 * Embedded context help tooltip functionality is incorporated this class.</p>
 *
 * <p>A simple example of the tooltip usage is the following:<br/>
 * <code>new HelpTooltip().<br/>
 *  &nbsp;&nbsp;&nbsp;&nbsp;setTitle("Title").<br/>
 *  &nbsp;&nbsp;&nbsp;&nbsp;setShortcut("Shortcut").<br/>
 *  &nbsp;&nbsp;&nbsp;&nbsp;setDescription("Description").<br/>
 *  &nbsp;&nbsp;&nbsp;&nbsp;installOn(component);</code>
 * </p>
 *
 * <h2>Restrictions and field formats</h2>
 * <p>If you’re creating a tooltip with a shortcut then title is mandatory otherwise title, description, link are optional.
 * You can optionally set the tooltip relative location using {@link HelpTooltip#setLocation(Alignment)}.
 * The <code>Alignment</code> enum defines fixed relative locations according to the design document (see the link below).
 * More types of relative location will be added as needed but there won’t be a way to choose the location on pixel basis.</p>
 *
 * <p>No HTML tagging is allowed in title or shortcut, they are supposed to be simple text strings.</p>
 * <p>Description is can be html formatted. You can use all possible html tagging in description just without enclosing
 * &lt;html&gt; and &lt;/html&gt; tags themselves. In description it's allowed to have &lt;p/ or &lt;p&gt; tags between paragraphs.
 * Paragraphs will be rendered with the standard (10px) offset from the title, from one another and from the link.
 * To force the line break in a paragraph use &lt;br/&gt;. Standard font coloring and styling is also available.</p>
 *
 * <h2>Timeouts</h2>
 *
 * <p>Single line tooltips autoclose in 10 seconds, multiline in 30 seconds. You can optionally disable autoclosing by
 * setting {@link HelpTooltip#setNeverHideOnTimeout(boolean)} to <code>true</code>. By default tooltips don’t close after a timeout on help buttons
 * (those having a round icon with question mark). Before setting this option to true you should contact designers first.</p>
 *
 * <p>System wide tooltip timeouts are set through the registry:
 * <ul>
 * <li>&nbsp;ide.helptooltip.full.dismissDelay - multiline tooltip timeout (default 30 seconds)</li>
 * <li>&nbsp;ide.helptooltip.regular.dismissDelay - single line tooltip timeout (default 10 seconds)</li>
 * </ul></p>
 *
 * <h2>Avoiding multiple popups</h2>
 * <p>Some actions may open a popup menu. Current design is that the action's popup menu should take over the help tooltip.
 * This is partly implemented in <code>AbstractPopup</code> class to track such cases. But this doesn’t always work.
 * If help tooltip shows up over the component’s popup menu you should make sure you set the master popup for the help tooltip.
 * This will prevent help tooltip from showing when the popup menu is opened. The best way to do it is to take source component
 * from an <code>InputEvent</code> and pass the source component along with the popup menu reference to
 * {@link HelpTooltip#setMasterPopup(Component, JBPopup)} static method.
 *
 * <p>If you’re handling <code>DumbAware.actionPerformed(AnActionEvent e)</code>, it has <code>InputEvent</code>in <code>AnActionEvent</code> which you can use to get the source.</p>
 *
 * <h2>ContextHelpLabel</h2>
 * <p>ContextHelpLabel is a convenient <code>JLabel</code> which contains a help icon and has a HelpTooltip installed on it.
 * You can create it using one of its static methods and pass title/description/link. This label can also be used in forms.
 * The UI designer will offer to create <code>private void createUIComponents()</code> method where you can create the label with a static method.</p>
 */

public class HelpTooltip implements Disposable {
  private static Color BACKGROUND_COLOR = new JBColor(Gray.xF7, new Color(0x474a4c));
  private static Color FONT_COLOR = new JBColor(() -> UIUtil.isUnderDarcula() ? Gray.xBF : SystemInfo.isMac ? Gray.x33 : Gray.x1A);
  private static Color SHORTCUT_COLOR = new JBColor(Gray.x78, Gray.x87);
  private static Color BORDER_COLOR = new JBColor(Gray.xAD, new Color(0x636569));

  private static Border DEFAULT_BORDER = SystemInfo.isMac ?     JBUI.Borders.empty(9, 10, 11, 16) :
                                         SystemInfo.isWindows ? JBUI.Borders.empty(7, 10, 10, 16):
                                                                JBUI.Borders.empty(10, 10, 10, 16);

  private static Border SMALL_BORDER = SystemInfo.isMac ? JBUI.Borders.empty(4, 8, 5, 8) :
                                       SystemInfo.isWindows ? JBUI.Borders.empty(4, 8, 6, 8) :
                                       JBUI.Borders.empty(5, 8, 4, 8);

  private static final int VGAP = JBUI.scale(UIUtil.DEFAULT_VGAP);
  private static final int HGAP = JBUI.scale(UIUtil.DEFAULT_HGAP);
  private static final int MAX_WIDTH = JBUI.scale(250);

  private static final String DOTS = "...";
  private static final String PARAGRAPH_SPLITTER = "<p/?>";

  private static final String TOOLTIP_PROPERTY = "JComponent.helpTooltip";

  private String title;
  private String shortcut;
  private String description;
  private LinkLabel link;
  private boolean neverHide;
  private Alignment alignment = Alignment.BOTTOM;

  private JComponent owner;
  private JBPopup masterPopup;
  private ComponentPopupBuilder myPopupBuilder;
  private JBPopup myPopup;
  private Alarm popupAlarm = new Alarm();
  private boolean isOverPopup;
  private boolean isMultiline;
  private int myDismissDelay;

  private MouseAdapter myMouseListener;

  /**
   * Location of the HelpTooltip relatively to the owner component.
   */
  public enum Alignment {
    RIGHT {
      @Override public Point getPointFor(JComponent owner) {
        Dimension size = owner.getSize();
        return new Point(size.width + JBUI.scale(1) - xOffset + VGAP, JBUI.scale(1) + yOffset);
      }
    },

    BOTTOM {
      @Override public Point getPointFor(JComponent owner) {
        Dimension size = owner.getSize();
        return new Point(JBUI.scale(1) + xOffset, JBUI.scale(1) + size.height - yOffset + VGAP);
      }
    },

    HELP_BUTTON {
      @Override public Point getPointFor(JComponent owner) {
        Dimension size = owner.getSize();
        return new Point(xOffset - JBUI.scale(5), JBUI.scale(1) + size.height - yOffset + VGAP);
      }
    };

    protected final int xOffset = JBUI.scale(UIManager.getInt("HelpTooltip.xOffset"));
    protected final int yOffset = JBUI.scale(UIManager.getInt("HelpTooltip.yOffset"));

    public abstract Point getPointFor(JComponent owner);
  }

  /**
   * Sets tooltip title. If it's longer than 2 lines (fitting in 250 pixels each) then
   * the text is automatically stripped to the word boundary and dots are added to the end.
   *
   * @param title text for title.
   * @return <code>this</code>
   */
  public HelpTooltip setTitle(String title) {
    this.title = title;
    return this;
  }

  /**
   * Sets text for the shortcut placeholder.
   *
   * @param shortcut text for shortcut.
   * @return <code>this</code>
   */
  public HelpTooltip setShortcut(String shortcut) {
    this.shortcut = shortcut;
    return this;
  }

  /**
   * Sets description text.
   *
   * @param description text for description.
   * @return <code>this</code>
   */
  public HelpTooltip setDescription(String description) {
    this.description = description;
    return this;
  }

  /**
   * Enables link in the tooltip below description and sets action for it.
   *
   * @param linkText text to show in the link.
   * @param linkAction action to execute when link is clicked.
   * @return <code>this</code>
   */
  public HelpTooltip setLink(String linkText, Runnable linkAction) {
    this.link = LinkLabel.create(linkText, () -> {
      hidePopup(true);
      linkAction.run();
    });
    return this;
  }

  /**
   * Toggles whether to hide tooltip automatically on timeout. For default behaviour just don't call this method.
   *
   * @param neverHide <code>true</code> don't hide, <code>false</code> otherwise.
   * @return <code>this</code>
   */
  public HelpTooltip setNeverHideOnTimeout(boolean neverHide) {
    this.neverHide = neverHide;
    return this;
  }

  /**
   * Sets location of the tooltip relatively to the owner component.
   *
   * @param alignment is relative location
   * @return <code>this</code>
   */
  public HelpTooltip setLocation(Alignment alignment) {
    this.alignment = alignment;
    return this;
  }

  /**
   * Installs the tooltip after the configuration has been completed on the specified owner component.
   *
   * @param component is the owner component for the tooltip.
   */
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
      isMultiline = pa.length > 1;
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

    component.addPropertyChangeListener("ancestor", evt -> {
      if (evt.getNewValue() == null) {
        hidePopup(true);
        Disposer.dispose(this);
      } else {
        registerOn((JComponent)evt.getSource());
      }
    });

    registerOn(component);
  }

  private void registerOn(JComponent component) {
    owner = component;
    owner.putClientProperty(TOOLTIP_PROPERTY, this);
    owner.addMouseListener(myMouseListener);
    owner.addMouseMotionListener(myMouseListener);
  }

  @Override
  public void dispose() {
    if (owner != null) {
      owner.removeMouseListener(myMouseListener);
      owner.removeMouseMotionListener(myMouseListener);
      owner.putClientProperty(TOOLTIP_PROPERTY, null);
      owner = null;
      masterPopup = null;
    }
  }

  /**
   * Hides and disposes the tooltip possibly installed on the mentioned component.Disposing means
   * unregistering all <code>HelpTooltip</code> specific listeners installed on the component.
   * If there is no tooltip installed on the component nothing happens.
   *
   * @param owner a possible <code>HelpTooltip</code> owner.
   */
  public static void dispose(@NotNull Component owner) {
    if (owner instanceof JComponent) {
      HelpTooltip instance = (HelpTooltip)((JComponent)owner).getClientProperty(TOOLTIP_PROPERTY);
      if (instance != null) {
        instance.hidePopup(true);
        Disposer.dispose(instance);
      }
    }
  }

  /**
   * Hides the tooltip possibly installed on the mentioned component without disposing.
   * Listeners are not removed.
   * If there is no tooltip installed on the component nothing happens.
   *
   * @param owner a possible <code>HelpTooltip</code> owner.
   */
  public static void hide(@NotNull Component owner) {
    if (owner instanceof JComponent) {
      HelpTooltip instance = (HelpTooltip)((JComponent)owner).getClientProperty(TOOLTIP_PROPERTY);
      if (instance != null) {
        instance.hidePopup(true);
      }
    }
  }

  /**
   * Sets master popup for the current <code>HelpTooltip</code>. Master popup takes over the help tooltip,
   * so when the master popup is about to be shown help tooltip hides.
   *
   * @param owner possible owner
   * @param master master popup
   */
  public static void setMasterPopup(@NotNull Component owner, JBPopup master) {
    if (owner instanceof JComponent) {
      HelpTooltip instance = (HelpTooltip)((JComponent)owner).getClientProperty(TOOLTIP_PROPERTY);
      if (instance != null) {
        instance.masterPopup = master;
      }
    }
  }

  private void scheduleShow(int delay) {
    popupAlarm.cancelAllRequests();
      popupAlarm.addRequest(() -> {
        if (canShow()) {
          myPopup = myPopupBuilder.createPopup();
          myPopup.show(new RelativePoint(owner, alignment.getPointFor(owner)));
          if (!neverHide) {
            scheduleHide(true, myDismissDelay);
          }
        }
      }, delay);
  }

  private boolean canShow() {
    return masterPopup == null || !masterPopup.isVisible();
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
