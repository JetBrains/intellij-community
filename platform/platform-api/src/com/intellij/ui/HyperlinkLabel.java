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

package com.intellij.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

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
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public class HyperlinkLabel extends HighlightableComponent {
  private static final TextAttributes BOLD_ATTRIBUTES = new TextAttributes(new JBColor(() -> {
    final Color foreground1 = UIUtil.getLabelTextForeground();
    return foreground1 == null ? UIUtil.getLabelForeground() : foreground1;
  }), null, null, null, Font.BOLD);

  private static final Logger LOG = Logger.getInstance(HyperlinkLabel.class.getName());

  private UIUtil.FontSize myFontSize;
  private HighlightedText myHighlightedText;
  private final List<HyperlinkListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myUseIconAsLink;
  private final TextAttributes myAnchorAttributes;
  private HyperlinkListener myHyperlinkListener = null;

  private boolean myMouseHover;
  private boolean myMousePressed;

  public HyperlinkLabel() {
    this("");
  }

  public HyperlinkLabel(String text) {
    this(text, UIUtil.getLabelBackground());
  }
  
  public HyperlinkLabel(String text, Color background) {
    this(text, PlatformColors.BLUE, background, PlatformColors.BLUE);
  }

  public HyperlinkLabel(String text, final Color textForegroundColor, final Color textBackgroundColor, final Color textEffectColor) {
    myAnchorAttributes = UIUtil.isUnderWin10LookAndFeel() ?
      new Win10TextAttributes(textBackgroundColor) :
      new TextAttributes(textForegroundColor, textBackgroundColor, textEffectColor, EffectType.LINE_UNDERSCORE, Font.PLAIN);

    enforceBackgroundOutsideText(textBackgroundColor);
    setHyperlinkText(text);
    enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    setOpaque(false);
  }

  public void addNotify() {
    super.addNotify();
    adjustSize();
  }

  public void setFontSize(@Nullable UIUtil.FontSize fontSize) {
    myFontSize = fontSize;
  }

  public void setHyperlinkText(String text) {
    setHyperlinkText("", text, "");
  }

  public void setHyperlinkText(String beforeLinkText, String linkText, String afterLinkText) {
    myUseIconAsLink = beforeLinkText.length() == 0;
    prepareText(beforeLinkText, linkText, afterLinkText);
  }

  public void setUseIconAsLink(boolean useIconAsLink) {
    myUseIconAsLink = useIconAsLink;
  }

  protected void adjustSize() {
    final Dimension preferredSize = this.getPreferredSize();
    this.setMinimumSize(preferredSize);
  }

  @Override
  protected void processComponentKeyEvent(KeyEvent event) {
    if (event.getModifiers() == 0 && event.getKeyCode() == KeyEvent.VK_SPACE) {
      event.consume();
      fireHyperlinkEvent();
    }
  }

  protected void processMouseEvent(MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_ENTERED && isOnLink(e.getX())) {
      myMouseHover = true;
      repaint();
    } else if (e.getID() == MouseEvent.MOUSE_EXITED) {
      setCursor(Cursor.getDefaultCursor());
      myMouseHover = false;
      myMousePressed = false;
      repaint();
    } else if (UIUtil.isActionClick(e, MouseEvent.MOUSE_PRESSED) && isOnLink(e.getX())) {
      fireHyperlinkEvent();
      myMousePressed = true;
      repaint();
    } else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
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
      setCursor(myMouseHover ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());

      if (needRepaint) {
        repaint();
      }
    }
    super.processMouseMotionEvent(e);
  }

  private boolean isOnLink(int x) {
    if (myUseIconAsLink && myIcon != null && x < myIcon.getIconWidth()) {
      return true;
    }
    final HighlightedRegion region = findRegionByX(x);
    return region != null && region.textAttributes == myAnchorAttributes;
  }

  private void prepareText(String beforeLinkText, String linkText, String afterLinkText) {
    applyFont();
    myHighlightedText = new HighlightedText();
    myHighlightedText.appendText(beforeLinkText, null);
    myHighlightedText.appendText(linkText, myAnchorAttributes);
    myHighlightedText.appendText(afterLinkText, null);
    myHighlightedText.applyToComponent(this);
    updateOnTextChange();
  }

  @Override
  public void setText(String text) {
    applyFont();
    myUseIconAsLink = false;
    super.setText(text);
    updateOnTextChange();
  }

  public void setHyperlinkTarget(@Nullable final String url) {
    if (myHyperlinkListener != null) {
      removeHyperlinkListener(myHyperlinkListener);
    }
    if (url != null) {
      myHyperlinkListener = new HyperlinkListener() {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
          BrowserUtil.browse(url);
        }
      };
      addHyperlinkListener(myHyperlinkListener);
    }
  }

  public void addHyperlinkListener(HyperlinkListener listener) {
    myListeners.add(listener);
  }

  public void removeHyperlinkListener(HyperlinkListener listener) {
    myListeners.remove(listener);
  }

  public String getText() {
    return myHighlightedText.getText();
  }

  protected void fireHyperlinkEvent() {
    HyperlinkEvent e = new HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED, null, null);
    for (HyperlinkListener listener : myListeners) {
      listener.hyperlinkUpdate(e);
    }
  }

  public void doClick() {
    fireHyperlinkEvent();
  }

  public void setHtmlText(String text) {
    HTMLEditorKit.Parser parse = new ParserDelegator();
    final HighlightedText highlightedText = new HighlightedText();
    try {
      parse.parse(new StringReader(text), new HTMLEditorKit.ParserCallback() {
        private TextAttributes currentAttributes = null;

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
    setFont(myFontSize == null ? UIUtil.getLabelFont() : UIUtil.getLabelFont(myFontSize));
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleHyperlinkLabel();
    }
    return accessibleContext;
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
      else {
        return null;
      }
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

  private class Win10TextAttributes extends TextAttributes {
    private Win10TextAttributes(Color textBackgroundColor) {
      super(null, textBackgroundColor, null, null, Font.PLAIN);
    }

    @Override public Color getForegroundColor() {
      return !isEnabled() ? UIManager.getColor("Label.disabledForeground") :
             myMousePressed ? UIManager.getColor("link.pressed.foreground") :
             myMouseHover ? UIManager.getColor("link.hover.foreground") :
             UIManager.getColor("link.foreground");
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
