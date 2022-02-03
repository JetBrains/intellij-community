// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsContexts.LinkLabel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

public class HyperlinkLabel extends HighlightableComponent {
  private static final TextAttributes BOLD_ATTRIBUTES = new TextAttributes(JBColor.lazy(() -> {
    final Color foreground1 = UIUtil.getLabelTextForeground();
    return foreground1 == null ? UIUtil.getLabelForeground() : foreground1;
  }), null, null, null, Font.BOLD);

  private static final Logger LOG = Logger.getInstance(HyperlinkLabel.class.getName());

  private static final String startTag = "<hyperlink>";
  private static final String finishTag = "</hyperlink>";

  private UIUtil.FontSize myFontSize;
  private HighlightedText myHighlightedText;
  private final List<HyperlinkListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myUseIconAsLink;
  private final TextAttributes myAnchorAttributes;
  private HyperlinkListener myHyperlinkListener;

  private boolean myMouseHover;
  private boolean myMousePressed;

  public HyperlinkLabel() {
    this("");
  }

  public HyperlinkLabel(@LinkLabel String text) {
    this(text, UIUtil.getLabelBackground());
  }

  public HyperlinkLabel(@LinkLabel String text, Color background) {
    this(text, PlatformColors.BLUE, background, PlatformColors.BLUE);
  }

  public HyperlinkLabel(@LinkLabel String text, final Color textForegroundColor, final Color textBackgroundColor, final Color textEffectColor) {
    myAnchorAttributes = StartupUiUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF() ?
                         new CustomTextAttributes(textBackgroundColor) :
                         new TextAttributes(textForegroundColor, textBackgroundColor, textEffectColor, EffectType.LINE_UNDERSCORE, Font.PLAIN);

    enforceBackgroundOutsideText(textBackgroundColor);
    setHyperlinkText(text);
    enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    setOpaque(false);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    adjustSize();
  }

  public void setFontSize(@Nullable UIUtil.FontSize fontSize) {
    myFontSize = fontSize;
  }

  public void setHyperlinkText(@LinkLabel String text) {
    doSetHyperLinkText("", text, "");
  }

  /**
   * @deprecated please use {@link HyperlinkLabel#setTextWithHyperlink(String) with "beforeLinkText<hyperlink>linkText</hyperlink>" instead}
   */
  @Deprecated
  public void setHyperlinkText(@LinkLabel String beforeLinkText, @LinkLabel String linkText, @LinkLabel String afterLinkText) {
    doSetHyperLinkText(beforeLinkText, linkText, afterLinkText);
  }

  @ApiStatus.Experimental
  public void setTextWithHyperlink(@NotNull @LinkLabel String text) {
    int startTagOffset = text.indexOf(startTag);
    if (startTagOffset == -1){
      LOG.error("Text \"" + text + "\" doesn't contain <hyperlink> tag");
      return;
    }

    int finishTagOffset = text.indexOf(finishTag);
    if (finishTagOffset == -1) {
      LOG.error("Text \"" + text + "\" doesn't contain </hyperlink> tag");
      return;
    }

    String beforeLinkText = StringUtil.unescapeXmlEntities(text.substring(0, startTagOffset));
    String linkText = StringUtil.unescapeXmlEntities(text.substring(startTagOffset + startTag.length(), finishTagOffset));
    String afterLinkText = StringUtil.unescapeXmlEntities(text.substring(finishTagOffset + finishTag.length()));

    doSetHyperLinkText(beforeLinkText, linkText, afterLinkText);
  }

  private void doSetHyperLinkText(@NotNull @LinkLabel String beforeLinkText,
                                  @NotNull @LinkLabel String linkText,
                                  @NotNull @LinkLabel String afterLinkText) {
    myUseIconAsLink = beforeLinkText.isEmpty();
    prepareText(beforeLinkText, linkText, afterLinkText);
  }

  public void setUseIconAsLink(boolean useIconAsLink) {
    myUseIconAsLink = useIconAsLink;
  }

  protected void adjustSize() {
    final Dimension preferredSize = getPreferredSize();
    setMinimumSize(preferredSize);
  }

  @Override
  protected void processComponentKeyEvent(KeyEvent event) {
    if (event.getModifiers() == 0 && event.getKeyCode() == KeyEvent.VK_SPACE) {
      event.consume();
      fireHyperlinkEvent(event);
    }
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_ENTERED && isOnLink(e.getX())) {
      myMouseHover = true;
      repaint();
    } else if (e.getID() == MouseEvent.MOUSE_EXITED) {
      setCursor(null);
      myMouseHover = false;
      myMousePressed = false;
      repaint();
    } else if (UIUtil.isActionClick(e, MouseEvent.MOUSE_PRESSED) && isOnLink(e.getX())) {
      myMousePressed = true;
      repaint();
    } else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
      if (myMousePressed && isOnLink(e.getX())) {
        fireHyperlinkEvent(e);
      }
      myMousePressed = false;
      repaint();
    }
    super.processMouseEvent(e);
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_MOVED) {
      boolean onLink = isOnLink(e.getX());
      boolean needRepaint = myMouseHover != onLink;
      myMouseHover = onLink;
      setCursor(myMouseHover ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);

      if (needRepaint) {
        repaint();
      }
    }
    super.processMouseMotionEvent(e);
  }

  private boolean isOnLink(int x) {
    if (myUseIconAsLink && myIcon != null) {
      int offset = getIconOffset();
      if (x >= offset && x < offset + myIcon.getIconWidth()) return true;
    }
    final HighlightedRegion region = findRegionByX(x);
    return region != null && region.textAttributes == myAnchorAttributes;
  }

  private void prepareText(@Nls String beforeLinkText, @Nls String linkText, @Nls String afterLinkText) {
    applyFont();
    myHighlightedText = new HighlightedText();
    myHighlightedText.appendText(beforeLinkText, null);
    myHighlightedText.appendText(linkText, myAnchorAttributes);
    myHighlightedText.appendText(afterLinkText, null);
    myHighlightedText.applyToComponent(this);
    updateOnTextChange();
  }

  @Override
  public void setText(@Nullable @Nls String text) {
    applyFont();
    myUseIconAsLink = false;
    super.setText(text);
    updateOnTextChange();
  }

  public void setHyperlinkTarget(@NonNls @Nullable final String url) {
    if (myHyperlinkListener != null) {
      removeHyperlinkListener(myHyperlinkListener);
    }
    if (url != null) {
      myHyperlinkListener = e -> BrowserUtil.browse(url);
      addHyperlinkListener(myHyperlinkListener);
      setIcon(AllIcons.Ide.External_link_arrow);
      setIconAtRight(true);
    }
  }

  public void addHyperlinkListener(HyperlinkListener listener) {
    myListeners.add(listener);
  }

  public void removeHyperlinkListener(HyperlinkListener listener) {
    myListeners.remove(listener);
  }

  @NotNull
  public @LinkLabel String getText() {
    return myHighlightedText.getText();
  }

  protected void fireHyperlinkEvent(@Nullable InputEvent inputEvent) {
    HyperlinkEvent e = new HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED, null, null, null, inputEvent);
    for (HyperlinkListener listener : myListeners) {
      listener.hyperlinkUpdate(e);
    }
  }

  public void doClick() {
    fireHyperlinkEvent(null);
  }

  public void setHtmlText(@LinkLabel String text) {
    HTMLEditorKit.Parser parse = new ParserDelegator();
    final HighlightedText highlightedText = new HighlightedText();
    try {
      parse.parse(new StringReader(text), new HTMLEditorKit.ParserCallback() {
        private TextAttributes currentAttributes;

        @Override
        public void handleText(char[] data, int pos) {
          highlightedText.appendText(data, currentAttributes);
        }

        @Override
        public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
          if (t == HTML.Tag.B) {
            currentAttributes = BOLD_ATTRIBUTES;
          }
          else if (t == HTML.Tag.A) {
            currentAttributes = myAnchorAttributes;
          }
        }

        @Override
        public void handleEndTag(HTML.Tag t, int pos) {
          currentAttributes = null;
        }
      }, false);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    highlightedText.applyToComponent(this);
    updateOnTextChange();
  }

  private void updateOnTextChange() {
    final JComponent parent = (JComponent)getParent();
    if (parent != null) {
      parent.revalidate();
      parent.repaint();
    }
    adjustSize();
  }

  public static class Croppable extends HyperlinkLabel {
    @Override
    protected void adjustSize() {
      // ignore, keep minimum size default
    }
  }

  @Override
  public void updateUI() {
    super.updateUI();
    applyFont();
  }

  private void applyFont() {
    setFont(myFontSize == null ? StartupUiUtil.getLabelFont() : UIUtil.getLabelFont(myFontSize));
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleHyperlinkLabel();
    }
    return accessibleContext;
  }

  @Override
  public void removeNotify() {
    myMouseHover = false;
    super.removeNotify();
  }

  /**
   * Hyperlink accessibility: "HYPERLINK" role and expose a "click" action.
   * @see AbstractButton.AccessibleAbstractButton
   */
  protected class AccessibleHyperlinkLabel extends AccessibleHighlightable implements AccessibleAction {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.HYPERLINK;
    }

    @Override
    public AccessibleAction getAccessibleAction() {
      return this;
    }

    @Override
    public int getAccessibleActionCount() {
      return 1;
    }

    @Override
    public String getAccessibleActionDescription(int i) {
      if (i == 0) {
        return UIManager.getString("AbstractButton.clickText");
      }
      return null;
    }

    @Override
    public boolean doAccessibleAction(int i) {
      if (i == 0) {
        doClick();
        return true;
      } else {
        return false;
      }
    }
  }

  private final class CustomTextAttributes extends TextAttributes {
    private CustomTextAttributes(Color textBackgroundColor) {
      super(null, textBackgroundColor, null, null, Font.PLAIN);
    }

    @Override public Color getForegroundColor() {
      return !isEnabled() ? UIManager.getColor("Label.disabledForeground") :
             myMousePressed ? JBUI.CurrentTheme.Link.Foreground.PRESSED :
             myMouseHover ? JBUI.CurrentTheme.Link.Foreground.HOVERED :
             JBUI.CurrentTheme.Link.Foreground.ENABLED;
    }

    @Override public Color getEffectColor() {
      return getForegroundColor();
    }

    @Override public EffectType getEffectType() {
      return !isEnabled() || myMouseHover || myMousePressed ? EffectType.LINE_UNDERSCORE : null;
    }

    @Override public void setForegroundColor(Color color) {
      throw new UnsupportedOperationException();
    }
    @Override public void setEffectColor(Color color) {
      throw new UnsupportedOperationException();
    }
    @Override public void setEffectType(EffectType effectType) {
      throw new UnsupportedOperationException();
    }
  }
}
