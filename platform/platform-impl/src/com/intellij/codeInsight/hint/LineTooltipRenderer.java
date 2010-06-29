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
package com.intellij.codeInsight.hint;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author cdr
 */
public class LineTooltipRenderer implements TooltipRenderer {
  @NonNls protected String myText;

  private boolean myActiveLink = false;
  private int myCurrentWidth;
  @NonNls protected static final String BORDER_LINE = "<hr size=1 noshade>";

  public LineTooltipRenderer(String text) {
    myText = text;
  }

  public LineTooltipRenderer(final String text, final int width) {
    this(text);
    myCurrentWidth = width;
  }

  public LightweightHint show(final Editor editor, final Point p, final boolean alignToRight, final TooltipGroup group) {
    if (myText == null) return null;

    //setup text
    myText = myText.replaceAll(String.valueOf(UIUtil.MNEMONIC), "");
    final boolean expanded = myCurrentWidth > 0 && dressDescription(editor);

    //pane
    final JEditorPane pane = initPane(myText);
    pane.setCaretPosition(0);
    final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    final JComponent contentComponent = editor.getContentComponent();

    final JComponent editorComponent = editor.getComponent();
    final JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

    int widthLimit = layeredPane.getWidth() - 10;
    int heightLimit = layeredPane.getHeight() - 5;

    final JBScrollPane scrollPane = ScrollPaneFactory.createScrollPane(pane);
    scrollPane.setBorder(null);
    int width = expanded ? 3 * myCurrentWidth / 2 : pane.getPreferredSize().width;
    int height = expanded ? Math.max(pane.getPreferredSize().height, 150) : pane.getPreferredSize().height;

    if (alignToRight) {
      p.x = Math.max(0, p.x - width);
    }

    // try to make cursor outside tooltip. SCR 15038
    p.x += 3;
    p.y += 3;

    if (p.x >= widthLimit - width) {
      p.x = widthLimit - width;
      width = Math.min(width, widthLimit);
      height += 20;
    }

    if (p.x < 3) {
      p.x = 3;
    }

    if (p.y > heightLimit - height) {
      p.y = heightLimit - height;
      height = Math.min(heightLimit, height);
    }

    if (p.y < 3) {
      p.y = 3;
    }

    locateOutsideMouseCursor(editor, layeredPane, p, width, height, heightLimit);

    // in order to restrict tooltip size
    pane.setSize(width, height);
    pane.setMaximumSize(new Dimension(width, height));
    pane.setMinimumSize(new Dimension(width, height));
    pane.setPreferredSize(new Dimension(width, height));

    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

    final Ref<AnAction> anAction = new Ref<AnAction>();
    final LightweightHint hint = new LightweightHint(scrollPane) {
      public void hide() {
        onHide(pane);
        super.hide();
        final AnAction action = anAction.get();
        if (action != null) {
          action.unregisterCustomShortcutSet(contentComponent);
        }
      }
    };
    anAction.set(new AnAction() { //action to expand description when tooltip was shown after mouse move; need to unregister from editor component
      {
        registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)), contentComponent);
      }
      public void actionPerformed(final AnActionEvent e) {
        hint.hide();
        if (myCurrentWidth > 0) {
          stripDescription();
        }
        createRenderer(myText, myCurrentWidth > 0 ? 0 : pane.getWidth()).show(editor, new Point(p.x -3, p.y -3), false, group);
      }
    });

    pane.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        myActiveLink = true;
        if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
          myActiveLink = false;
          return;
        }
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          if (!expanded) { // more -> less
            for (final TooltipLinkHandlerEP handlerEP : Extensions.getExtensions(TooltipLinkHandlerEP.EP_NAME)) {
              if (handlerEP.handleLink(e.getDescription(), editor, pane)) {
                myText = convertTextOnLinkHandled(myText);
                pane.setText(myText);
                return;
              }
            }
            if (e.getURL() != null) {
              BrowserUtil.launchBrowser(e.getURL().toString());
            }
          } else { //less -> more
            if (e.getURL() != null) {
              BrowserUtil.launchBrowser(e.getURL().toString());
              return;
            }
            stripDescription();
            hint.hide();
            createRenderer(myText, 0).show(editor, new Point(p.x - 3, p.y - 3), false, group);
          }
        }
      }
    });

    // This listener makes hint transparent for mouse events. It means that hint is closed
    // by MousePressed and this MousePressed goes into the underlying editor component.
    pane.addMouseListener(new MouseAdapter() {
      public void mouseReleased(final MouseEvent e) {
        if (!myActiveLink) {
          MouseEvent newMouseEvent = SwingUtilities.convertMouseEvent(e.getComponent(), e, contentComponent);
          hint.hide();
          contentComponent.dispatchEvent(newMouseEvent);
        }
      }

      public void mouseExited(final MouseEvent e) {
        if (!expanded) {
          hint.hide();
        }
      }
    });

    hintManager.showEditorHint(hint, editor, p,
                               HintManagerImpl.HIDE_BY_ANY_KEY | HintManagerImpl.HIDE_BY_TEXT_CHANGE | HintManagerImpl.HIDE_BY_OTHER_HINT |
                               HintManagerImpl.HIDE_BY_SCROLLING, 0, false);
    return hint;
  }

  private static void locateOutsideMouseCursor(Editor editor,
                                               JComponent editorComponent,
                                               Point p,
                                               int width,
                                               int height,
                                               int heightLimit) {
    Point mouse = MouseInfo.getPointerInfo().getLocation();
    SwingUtilities.convertPointFromScreen(mouse, editorComponent);
    Rectangle tooltipRect = new Rectangle(p, new Dimension(width, height));
    // should show at least one line apart
    tooltipRect.setBounds(tooltipRect.x, tooltipRect.y - editor.getLineHeight(), width, height + 2 * editor.getLineHeight());
    if (tooltipRect.contains(mouse)) {
      if (mouse.y + height + editor.getLineHeight() > heightLimit && mouse.y - height - editor.getLineHeight() > 0) {
        p.y = mouse.y - height - editor.getLineHeight();
      }
      else {
        p.y = mouse.y + editor.getLineHeight();
      }
    }
  }

  protected String convertTextOnLinkHandled(String text) {
    return text;
  }

  protected void onHide(JComponent contentComponent) {
  }

  protected LineTooltipRenderer createRenderer(String text, int width) {
    return new LineTooltipRenderer(text, width);
  }

  protected boolean dressDescription(Editor editor) { return false; }
  protected void stripDescription() {}

  static JEditorPane initPane(@NonNls String text) {
    text = "<html><head>" + UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()) + "</head><body>" + getHtmlBody(text) + "</body></html>";
    final JEditorPane pane = new JEditorPane(UIUtil.HTML_MIME, text);
    pane.setEditable(false);
    pane.setBorder(
      BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Color.black),
        BorderFactory.createEmptyBorder(0, 5, 0, 5)
      )
    );
    pane.setForeground(Color.black);
    pane.setBackground(HintUtil.INFORMATION_COLOR);
    pane.setOpaque(true);
    return pane;
  }

  public void addBelow(String text) {
    @NonNls String newBody;
    if (myText ==null) {
      newBody = getHtmlBody(text);
    }
    else {
      String html1 = getHtmlBody(myText);
      String html2 = getHtmlBody(text);
      newBody = html1 + BORDER_LINE + html2;
    }
    myText = "<html><body>" + newBody + "</body></html>";
  }

  protected static String getHtmlBody(@NonNls String text) {
    if (!text.startsWith("<html>")) {
      return text.replaceAll("\n","<br>");
    }
    final int bodyIdx = text.indexOf("<body>");
    final int closedBodyIdx = text.indexOf("</body>");
    if (bodyIdx != -1 && closedBodyIdx != -1) {
      return text.substring(bodyIdx + "<body>".length(), closedBodyIdx);
    }
    text = StringUtil.trimStart(text, "<html>").trim();
    text = StringUtil.trimEnd(text, "</html>").trim();
    text = StringUtil.trimStart(text, "<body>").trim();
    text = StringUtil.trimEnd(text, "</body>").trim();
    return text;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LineTooltipRenderer)) return false;

    final LineTooltipRenderer lineTooltipRenderer = (LineTooltipRenderer)o;

    return myText == null ? lineTooltipRenderer.myText == null : myText.equals(lineTooltipRenderer.myText);
  }

  public int hashCode() {
    return myText != null ? myText.hashCode() : 0;
  }

  public String getText() {
    return myText;
  }
}
