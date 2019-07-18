// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
import java.util.function.BooleanSupplier;

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
 * <p>If you're creating a tooltip with a shortcut then title is mandatory otherwise title, description, link are optional.
 * You can optionally set the tooltip relative location using {@link HelpTooltip#setLocation(Alignment)}.
 * The {@code Alignment} enum defines fixed relative locations according to the design document (see the link below).
 * More types of relative location will be added as needed but there won't be a way to choose the location on pixel basis.</p>
 *
 * <p>No HTML tagging is allowed in title or shortcut, they are supposed to be simple text strings.</p>
 * <p>Description is can be html formatted. You can use all possible html tagging in description just without enclosing
 * &lt;html&gt; and &lt;/html&gt; tags themselves. In description it's allowed to have &lt;p/&gt; or &lt;p&gt; tags between paragraphs.
 * Paragraphs will be rendered with the standard (10px) offset from the title, from one another and from the link.
 * To force the line break in a paragraph use &lt;br/&gt;. Standard font coloring and styling is also available.</p>
 *
 * <h2>Timeouts</h2>
 *
 * <p>Single line tooltips autoclose in 10 seconds, multiline in 30 seconds. You can optionally disable autoclosing by
 * setting {@link HelpTooltip#setNeverHideOnTimeout(boolean)} to {@code true}. By default tooltips don't close after a timeout on help buttons
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
 * This is partly implemented in {@code AbstractPopup} class to track such cases. But this doesn't always work.
 * If help tooltip shows up over the component's popup menu you should make sure you set the master popup for the help tooltip.
 * This will prevent help tooltip from showing when the popup menu is opened. The best way to do it is to take source component
 * from an {@code InputEvent} and pass the source component along with the popup menu reference to
 * {@link HelpTooltip#setMasterPopup(Component, JBPopup)} static method.
 *
 * <p>If you're handling {@code DumbAware.actionPerformed(AnActionEvent e)}, it has {@code InputEvent}in {@code AnActionEvent} which you can use to get the source.</p>
 *
 * <h2>ContextHelpLabel</h2>
 * <p>ContextHelpLabel is a convenient {@code JLabel} which contains a help icon and has a HelpTooltip installed on it.
 * You can create it using one of its static methods and pass title/description/link. This label can also be used in forms.
 * The UI designer will offer to create {@code private void createUIComponents()} method where you can create the label with a static method.</p>
 */

public class HelpTooltip {
  private static final Color BACKGROUND_COLOR = JBColor.namedColor("HelpTooltip.background", 0xf7f7f7);
  private static final Color FONT_COLOR = JBColor.namedColor("HelpTooltip.foreground", 0x1a1a1a);
  private static final Color SHORTCUT_COLOR = JBColor.namedColor("HelpTooltip.shortcutForeground", 0x787878);
  private static final Color BORDER_COLOR = JBColor.namedColor("HelpTooltip.borderColor", 0xadadad);

  private static final String DOTS = "...";
  private static final String PARAGRAPH_SPLITTER = "<p/?>";

  private static final String TOOLTIP_PROPERTY = "JComponent.helpTooltip";

  private String title;
  private String shortcut;
  private String description;
  private LinkLabel link;
  private boolean neverHide;
  private Alignment alignment = Alignment.BOTTOM;

  private BooleanSupplier masterPopupOpenCondition;

  private ComponentPopupBuilder myPopupBuilder;
  private Dimension myPopupSize;
  private JBPopup myPopup;
  private final Alarm popupAlarm = new Alarm();
  private boolean isOverPopup;
  private boolean isMultiline;
  private int myDismissDelay;

  private MouseAdapter myMouseListener;

  /**
   * Location of the HelpTooltip relatively to the owner component.
   */
  public enum Alignment {
    RIGHT {
      @Override public Point getPointFor(JComponent owner, Dimension popupSize) {
        Dimension size = owner.getSize();
        return new Point(size.width + JBUIScale.scale(5) - X_OFFSET.get(), JBUIScale.scale(1) + Y_OFFSET.get());
      }
    },

    BOTTOM {
      @Override public Point getPointFor(JComponent owner, Dimension popupSize) {
        Dimension size = owner.getSize();
        return new Point(JBUIScale.scale(1) + X_OFFSET.get(), JBUIScale.scale(5) + size.height - Y_OFFSET.get());
      }
    },

    HELP_BUTTON {
      @Override public Point getPointFor(JComponent owner, Dimension popupSize) {
        Insets i  = owner.getInsets();
        return new Point(X_OFFSET.get() - JBUIScale.scale(40), i.top + Y_OFFSET.get() - JBUIScale.scale(6) - popupSize.height);
      }
    };

    public abstract Point getPointFor(JComponent owner, Dimension popupSize);
  }

  /**
   * Sets tooltip title. If it's longer than 2 lines (fitting in 250 pixels each) then
   * the text is automatically stripped to the word boundary and dots are added to the end.
   *
   * @param title text for title.
   * @return {@code this}
   */
  public HelpTooltip setTitle(String title) {
    this.title = title;
    return this;
  }

  /**
   * Sets text for the shortcut placeholder.
   *
   * @param shortcut text for shortcut.
   * @return {@code this}
   */
  public HelpTooltip setShortcut(String shortcut) {
    this.shortcut = shortcut;
    return this;
  }

  /**
   * Sets description text.
   *
   * @param description text for description.
   * @return {@code this}
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
   * @return {@code this}
   */
  public HelpTooltip setLink(String linkText, Runnable linkAction) {
    link = LinkLabel.create(linkText, () -> {
      hidePopup(true);
      linkAction.run();
    });
    return this;
  }

  /**
   * Toggles whether to hide tooltip automatically on timeout. For default behaviour just don't call this method.
   *
   * @param neverHide {@code true} don't hide, {@code false} otherwise.
   * @return {@code this}
   */
  public HelpTooltip setNeverHideOnTimeout(boolean neverHide) {
    this.neverHide = neverHide;
    return this;
  }

  /**
   * Sets location of the tooltip relatively to the owner component.
   *
   * @param alignment is relative location
   * @return {@code this}
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
  public void installOn(@NotNull JComponent component) {
    myDismissDelay = Registry.intValue(isMultiline ? "ide.helptooltip.full.dismissDelay" : "ide.helptooltip.regular.dismissDelay");
    neverHide = neverHide || UIUtil.isHelpButton(component);

    myMouseListener = new MouseAdapter() {
      @Override public void mouseEntered(MouseEvent e) {
        if (myPopup != null && !myPopup.isDisposed()){
          myPopup.cancel();
        }
        scheduleShow((JComponent)e.getComponent(), Registry.intValue("ide.tooltip.initialReshowDelay"));
      }

      @Override public void mouseExited(MouseEvent e) {
        scheduleHide(link == null, Registry.intValue("ide.tooltip.initialDelay.highlighter"));
      }

      @Override public void mouseMoved(MouseEvent e) {
        if (myPopup == null || myPopup.isDisposed()) {
          scheduleShow((JComponent)e.getComponent(), Registry.intValue("ide.tooltip.reshowDelay"));
        }
      }
    };

    initPopupBuilder();

    component.putClientProperty(TOOLTIP_PROPERTY, this);
    installMouseListeners(component);
  }

  private void initPopupBuilder() {
    JComponent tipPanel = createTipPanel();
    myPopupSize = tipPanel.getPreferredSize();
    myPopupBuilder = JBPopupFactory.getInstance().
        createComponentPopupBuilder(tipPanel, null).setBorderColor(BORDER_COLOR).setShowShadow(false);
  }

  private JPanel createTipPanel() {
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

    tipPanel.setLayout(new VerticalLayout(VGAP.get()));
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
      link.setFont(modifyFont(link.getFont()));
      tipPanel.add(link, VerticalLayout.TOP);
    }

    isMultiline = isMultiline || StringUtil.isNotEmpty(description) && (StringUtil.isNotEmpty(title) || link != null);
    tipPanel.setBorder(textBorder(isMultiline));

    return tipPanel;
  }

  private void installMouseListeners(@NotNull JComponent owner) {
    owner.addMouseListener(myMouseListener);
    owner.addMouseMotionListener(myMouseListener);
  }

  private void uninstallMouseListeners(@NotNull JComponent owner) {
    owner.removeMouseListener(myMouseListener);
    owner.removeMouseMotionListener(myMouseListener);
  }

  /**
   * Hides and disposes the tooltip possibly installed on the mentioned component. Disposing means
   * unregistering all {@code HelpTooltip} specific listeners installed on the component.
   * If there is no tooltip installed on the component nothing happens.
   *
   * @param owner a possible {@code HelpTooltip} owner.
   */
  public static void dispose(@NotNull Component owner) {
    if (owner instanceof JComponent) {
      JComponent component = (JComponent)owner;
      HelpTooltip instance = (HelpTooltip)component.getClientProperty(TOOLTIP_PROPERTY);
      if (instance != null) {
        instance.hidePopup(true);
        instance.uninstallMouseListeners(component);

        component.putClientProperty(TOOLTIP_PROPERTY, null);
        instance.masterPopupOpenCondition = null;
      }
    }
  }

  /**
   * Hides the tooltip possibly installed on the mentioned component without disposing.
   * Listeners are not removed.
   * If there is no tooltip installed on the component nothing happens.
   *
   * @param owner a possible {@code HelpTooltip} owner.
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
   * Sets master popup for the current {@code HelpTooltip}. Master popup takes over the help tooltip,
   * so when the master popup is about to be shown help tooltip hides.
   *
   * @param owner possible owner
   * @param master master popup
   */
  public static void setMasterPopup(@NotNull Component owner, JBPopup master) {
    if (owner instanceof JComponent) {
      HelpTooltip instance = (HelpTooltip)((JComponent)owner).getClientProperty(TOOLTIP_PROPERTY);
      if (instance != null && instance.myPopup != master) {
        instance.masterPopupOpenCondition = () -> master == null || !master.isVisible();
      }
    }
  }

  /**
   * Sets master popup open condition supplier for the current {@code HelpTooltip}.
   * This method is more general than {@link HelpTooltip#setMasterPopup(Component, JBPopup)} so that
   * it's possible to create master popup condition for any types of popups such as {@code JPopupMenu}
   *
   * @param owner possible owner
   * @param condition a {@code BooleanSupplier} for open condition
   */
  @ApiStatus.Experimental
  public static void setMasterPopupOpenCondition(@NotNull Component owner, @Nullable BooleanSupplier condition) {
    if (owner instanceof JComponent) {
      HelpTooltip instance = (HelpTooltip)((JComponent)owner).getClientProperty(TOOLTIP_PROPERTY);
      if (instance != null) {
        instance.masterPopupOpenCondition = condition;
      }
    }
  }

  private void scheduleShow(JComponent owner, int delay) {
    popupAlarm.cancelAllRequests();
    popupAlarm.addRequest(() -> {
      if (masterPopupOpenCondition == null || masterPopupOpenCondition.getAsBoolean()) {
        myPopup = myPopupBuilder.createPopup();
        myPopup.show(new RelativePoint(owner, alignment.getPointFor(owner, myPopupSize)));
        if (!neverHide) {
          scheduleHide(true, myDismissDelay);
        }
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

  private static final JBValue VGAP = new JBValue.UIInteger("HelpTooltip.verticalGap", 4);
  private static final JBValue HGAP = new JBValue.UIInteger("HelpTooltip.horizontalGap", 10);
  private static final JBValue MAX_WIDTH = new JBValue.UIInteger("HelpTooltip.maxWidth", 250);
  private static final JBValue X_OFFSET = new JBValue.UIInteger("HelpTooltip.xOffset", 0);
  private static final JBValue Y_OFFSET = new JBValue.UIInteger("HelpTooltip.yOffset", 0);
  private static final JBValue FONT_DELTA_SIZE = new JBValue.UIInteger("HelpTooltip.fontSizeDelta", 0);

  private static Border textBorder(boolean isDefault) {
    Insets i = UIManager.getInsets(isDefault ? "HelpTooltip.defaultTextBorderInsets" : "HelpTooltip.smallTextBorderInsets");
    return i != null ? new JBEmptyBorder(i) : JBUI.Borders.empty();
  }

  private static Font modifyFont(Font font) {
    return font.deriveFont((float)font.getSize() + FONT_DELTA_SIZE.get());
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

      Font font = modifyFont(getFont());
      setFont(font);

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
      int titleWidth = UIUtilities.stringWidth(this, tfm, title);

      FontMetrics fm = getFontMetrics(font);
      titleWidth += StringUtil.isNotEmpty(shortcut) ? HGAP.get() + UIUtilities.stringWidth(this, fm, shortcut) : 0;

      boolean limitWidth = StringUtil.isNotEmpty(description) || link != null;
      isMultiline = limitWidth && titleWidth > MAX_WIDTH.get();
      setPreferredSize(isMultiline ? new Dimension(MAX_WIDTH.get(), tfm.getHeight() * 2) : new Dimension(titleWidth, fm.getHeight()));
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
            breakWidth -= dotLayout.getAdvance() + HGAP.get() + shortcutLayout.getAdvance();
          }

          layout = lineMeasurer.nextLayout(breakWidth);

          drawPosY += layout.getAscent();
          layout.draw(g2, 0, drawPosY);

          if (shortcutString != null) {
            dotLayout.draw(g2, layout.getAdvance(), drawPosY);

            g2.setColor(SHORTCUT_COLOR);
            shortcutLayout.draw(g2, layout.getAdvance() + dotLayout.getAdvance() + HGAP.get(), drawPosY);
          }
        } else if (layout != null && shortcutString != null) {
          g2.setColor(SHORTCUT_COLOR);
          if (Float.compare(getWidth() - layout.getAdvance(), shortcutLayout.getAdvance() + HGAP.get()) >= 0) {
            drawPosY = shortcutLayout.getAscent();
            shortcutLayout.draw(g2, layout.getAdvance() + HGAP.get(), drawPosY);
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
      setFont(modifyFont(getFont()));

      View v = BasicHTML.createHTMLView(this, String.format("<html>%s</html>", text));
      float width = v.getPreferredSpan(View.X_AXIS);
      isMultiline = isMultiline || width > MAX_WIDTH.get();
      setText(width > MAX_WIDTH.get() ?
              String.format("<html><div width=%d>%s</div></html>", MAX_WIDTH.get(), text) :
              String.format("<html>%s</html>", text));

      if (width > MAX_WIDTH.get()) {
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

    private void visit(@NotNull View v, Collection<? super View> result) {
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
