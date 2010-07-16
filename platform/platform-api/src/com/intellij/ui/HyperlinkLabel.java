/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public class HyperlinkLabel extends HighlightableComponent {
  private HighlightedText myHighlightedText;
  private final List<HyperlinkListener> myListeners = new ArrayList<HyperlinkListener>();
  private final Color myTextForegroundColor;
  private final Color myTextBackgroundColor;
  private final Color myTextEffectColor;

  public HyperlinkLabel() {
    this("");
  }

  public HyperlinkLabel(String text) {
    this(text, Color.BLUE, UIUtil.getLabelBackground(), Color.BLUE);
  }

  public HyperlinkLabel(String text, final Color textForegroundColor, final Color textBackgroundColor, final Color textEffectColor) {
    myTextForegroundColor = textForegroundColor;
    myTextBackgroundColor = textBackgroundColor;
    myTextEffectColor = textEffectColor;
    enforceBackgroundOutsideText(textBackgroundColor);
    setHyperlinkText(text);
    enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
  }

  public void addNotify() {
    super.addNotify();
    adjustSize();
  }

  public void setHyperlinkText(String text) {
    setHyperlinkText("", text, "");
  }

  public void setHyperlinkText(String beforeLinkText, String linkText, String afterLinkText) {
    prepareText(beforeLinkText, linkText, afterLinkText);
    revalidate();
    adjustSize();
  }

  private void adjustSize() {
    final Dimension preferredSize = this.getPreferredSize();
    this.setMinimumSize(preferredSize);
  }


  protected void processMouseEvent(MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_EXITED) {
      setCursor(Cursor.getDefaultCursor());
    }
    else if (UIUtil.isActionClick(e, MouseEvent.MOUSE_PRESSED) && isOnLink(e.getX())) {
      fireHyperlinkEvent();
    }
    super.processMouseEvent(e);
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_MOVED) {
      setCursor(isOnLink(e.getX()) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
    }
    super.processMouseMotionEvent(e);
  }

  private boolean isOnLink(int x) {
    return findRegionByX(x) != null;
  }

  private void prepareText(String beforeLinkText, String linkText, String afterLinkText) {
    setFont(UIUtil.getLabelFont());
    myHighlightedText = new HighlightedText();
    myHighlightedText.appendText(beforeLinkText, null);
    myHighlightedText.appendText(linkText, new TextAttributes(myTextForegroundColor, myTextBackgroundColor, myTextEffectColor,
                                                          EffectType.LINE_UNDERSCORE, Font.PLAIN));
    myHighlightedText.appendText(afterLinkText, null);
    myHighlightedText.applyToComponent(this);
    adjustSize();
  }

  public void setHyperlinkTarget(@NotNull final String url) {
    addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        BrowserUtil.launchBrowser(url);
      }
    });
  }

  public void addHyperlinkListener(HyperlinkListener listener) {
    myListeners.add(listener);
  }

  public void removeHyperlinkListener(HyperlinkListener listener) {
    myListeners.remove(listener);
  }

  String getText() {
    return myHighlightedText.getText();
  }

  protected void fireHyperlinkEvent() {
    HyperlinkEvent e = new HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED, null, null);
    HyperlinkListener[] listeners = myListeners.toArray(new HyperlinkListener[myListeners.size()]);
    for (HyperlinkListener listener : listeners) {
      listener.hyperlinkUpdate(e);
    }
  }
}
