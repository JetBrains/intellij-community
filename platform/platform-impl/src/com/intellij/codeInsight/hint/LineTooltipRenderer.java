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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.ComparableObject;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author cdr
 */
public class LineTooltipRenderer extends ComparableObject.Impl implements TooltipRenderer {
  @NonNls protected String myText;

  private boolean myActiveLink = false;
  private int myCurrentWidth;
  @NonNls protected static final String BORDER_LINE = "<hr size=1 noshade>";

  public LineTooltipRenderer(String text, Object[] comparable) {
    super(comparable);
    myText = text;
  }

  public LineTooltipRenderer(final String text, final int width, Object[] comparable) {
    this(text, comparable);
    myCurrentWidth = width;
  }

  public LightweightHint show(final Editor editor,
                              final Point p,
                              final boolean alignToRight,
                              final TooltipGroup group,
                              final HintHint hintHint) {
    if (myText == null) return null;

    //setup text
    myText = myText.replaceAll(String.valueOf(UIUtil.MNEMONIC), "");
    final boolean expanded = myCurrentWidth > 0 && dressDescription(editor);

    final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    final JComponent contentComponent = editor.getContentComponent();

    final JComponent editorComponent = editor.getComponent();
    final JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

    final JEditorPane pane = initPane(myText, hintHint, layeredPane);
    if (!hintHint.isAwtTooltip()) {
      correctLocation(editor, pane, p, alignToRight, expanded, myCurrentWidth);
    }

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(pane);
    scrollPane.setBorder(null);

    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

    scrollPane.setOpaque(hintHint.isOpaqueAllowed());
    scrollPane.getViewport().setOpaque(hintHint.isOpaqueAllowed());

    scrollPane.setBackground(hintHint.getTextBackground());
    scrollPane.getViewport().setBackground(hintHint.getTextBackground());

    scrollPane.setViewportBorder(null);

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
    anAction
      .set(new AnAction() { //action to expand description when tooltip was shown after mouse move; need to unregister from editor component

        {
          registerCustomShortcutSet(
            new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)),
            contentComponent);
        }

        public void actionPerformed(final AnActionEvent e) {
          hint.hide();
          if (myCurrentWidth > 0) {
            stripDescription();
          }

          TooltipController.getInstance().showTooltip(editor, new Point(p.x - 3, p.y - 3), createRenderer(myText, myCurrentWidth > 0 ? 0 : pane.getWidth()), alignToRight, group, hintHint);
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
          }
          else { //less -> more
            if (e.getURL() != null) {
              BrowserUtil.launchBrowser(e.getURL().toString());
              return;
            }
            stripDescription();
            hint.hide();
            TooltipController.getInstance().showTooltip(editor, new Point(p.x - 3, p.y - 3), createRenderer(myText, 0), false, group, hintHint);
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

    hintManager.showEditorHint(hint, editor, p, HintManager.HIDE_BY_ANY_KEY |
                                                HintManager.HIDE_BY_TEXT_CHANGE |
                                                HintManager.HIDE_BY_OTHER_HINT |
                                                HintManager.HIDE_BY_SCROLLING, 0, false, hintHint);
    return hint;
  }

  public static void correctLocation(Editor editor,
                                     JComponent tooltipComponent,
                                     Point p,
                                     boolean alignToRight,
                                     boolean expanded,
                                     int currentWidth) {
    final JComponent editorComponent = editor.getComponent();
    final JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

    int widthLimit = layeredPane.getWidth() - 10;
    int heightLimit = layeredPane.getHeight() - 5;

    Dimension dimension =
      correctLocation(editor, p, alignToRight, expanded, tooltipComponent, layeredPane, widthLimit, heightLimit, currentWidth);

    // in order to restrict tooltip size
    tooltipComponent.setSize(dimension);
    tooltipComponent.setMaximumSize(dimension);
    tooltipComponent.setMinimumSize(dimension);
    tooltipComponent.setPreferredSize(dimension);
  }

  private static Dimension correctLocation(Editor editor,
                                           Point p,
                                           boolean alignToRight,
                                           boolean expanded,
                                           JComponent tooltipComponent,
                                           JLayeredPane layeredPane,
                                           int widthLimit,
                                           int heightLimit,
                                           int currentWidth) {
    Dimension preferredSize = tooltipComponent.getPreferredSize();
    int width = expanded ? 3 * currentWidth / 2 : preferredSize.width;
    int height = expanded ? Math.max(preferredSize.height, 150) : preferredSize.height;
    Dimension dimension = new Dimension(width, height);

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
      dimension = new Dimension(width, height);
    }

    if (p.x < 3) {
      p.x = 3;
    }

    if (p.y > heightLimit - height) {
      p.y = heightLimit - height;
      height = Math.min(heightLimit, height);
      dimension = new Dimension(width, height);
    }

    if (p.y < 3) {
      p.y = 3;
    }

    locateOutsideMouseCursor(editor, layeredPane, p, width, height, heightLimit);
    return dimension;
  }

  private static void locateOutsideMouseCursor(Editor editor, JComponent editorComponent, Point p, int width, int height, int heightLimit) {
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
    return new LineTooltipRenderer(text, width, getEqualityObjects());
  }

  protected boolean dressDescription(Editor editor) {
    return false;
  }

  protected void stripDescription() {
  }

  static JEditorPane initPane(@NonNls String text, final HintHint hintHint, JLayeredPane layeredPane) {
    final Ref<Dimension> prefSize = new Ref<Dimension>(null);
    text = "<html><head>" +
           UIUtil.getCssFontDeclaration(hintHint.getTextFont(), hintHint.getTextForeground(), hintHint.getLinkForeground()) +
           "</head><body>" +
           getHtmlBody(text) +
           "</body></html>";

    final JEditorPane pane = new JEditorPane() {
      @Override
      public Dimension getPreferredSize() {
        return prefSize.get() != null ? prefSize.get() : super.getPreferredSize();
      }
    };

    final HTMLEditorKit.HTMLFactory factory = new HTMLEditorKit.HTMLFactory() {
      @Override
      public View create(Element elem) {
        AttributeSet attrs = elem.getAttributes();
        Object elementName = attrs.getAttribute(AbstractDocument.ElementNameAttribute);
        Object o = (elementName != null) ? null : attrs.getAttribute(StyleConstants.NameAttribute);
        if (o instanceof HTML.Tag) {
          HTML.Tag kind = (HTML.Tag)o;
          if (kind == HTML.Tag.HR) {
            return new CustomHrView(elem, hintHint.getTextForeground());
          }
        }
        return super.create(elem);
      }
    };

    HTMLEditorKit kit = new HTMLEditorKit() {
      @Override
      public ViewFactory getViewFactory() {
        return factory;
      }
    };
    pane.setEditorKit(kit);
    pane.setText(text);

    pane.setCaretPosition(0);
    pane.setEditable(false);

    if (hintHint.isOwnBorderAllowed()) {
      setBorder(pane);
      setColors(pane);
    }
    else {
      pane.setBorder(null);
    }

    if (hintHint.isAwtTooltip()) {
      Dimension size = layeredPane.getSize();
      int fitWidth = (int)(size.width * 0.8);
      Dimension prefSizeOriginal = pane.getPreferredSize();
      if (prefSizeOriginal.width > fitWidth) {
        pane.setSize(new Dimension(fitWidth, Integer.MAX_VALUE));
        Dimension fixedWidthSize = pane.getPreferredSize();
        prefSize.set(new Dimension(fitWidth, fixedWidthSize.height));
      }
      else {
        prefSize.set(prefSizeOriginal);
      }
    }

    pane.setOpaque(hintHint.isOpaqueAllowed());
    pane.setBackground(hintHint.getTextBackground());

    return pane;
  }


  public static void setColors(JComponent pane) {
    pane.setForeground(Color.black);
    pane.setBackground(HintUtil.INFORMATION_COLOR);
    pane.setOpaque(true);
  }

  public static void setBorder(JComponent pane) {
    pane.setBorder(
      BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.black), BorderFactory.createEmptyBorder(0, 5, 0, 5)));
  }

  public void addBelow(String text) {
    @NonNls String newBody;
    if (myText == null) {
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
    String result = text;
    if (!text.startsWith("<html>")) {
      result = text.replaceAll("\n", "<br>");
    }
    else {
      final int bodyIdx = text.indexOf("<body>");
      final int closedBodyIdx = text.indexOf("</body>");
      if (bodyIdx != -1 && closedBodyIdx != -1) {
        result = text.substring(bodyIdx + "<body>".length(), closedBodyIdx);
      }
      else {
        text = StringUtil.trimStart(text, "<html>").trim();
        text = StringUtil.trimEnd(text, "</html>").trim();
        text = StringUtil.trimStart(text, "<body>").trim();
        text = StringUtil.trimEnd(text, "</body>").trim();
        result = text;
      }
    }

    return result;
  }

  public String getText() {
    return myText;
  }
}
