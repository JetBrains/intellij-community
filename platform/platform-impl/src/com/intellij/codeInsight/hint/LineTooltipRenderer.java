/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.Html;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import com.intellij.util.ui.update.ComparableObject;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;

/**
 * @author cdr
 */
public class LineTooltipRenderer extends ComparableObject.Impl implements TooltipRenderer {

  @NonNls protected String myText;

  private boolean myActiveLink = false;
  private int myCurrentWidth;

  public LineTooltipRenderer(String text, Object[] comparable) {
    super(comparable);
    myText = text;
  }

  public LineTooltipRenderer(final String text, final int width, Object[] comparable) {
    this(text, comparable);
    myCurrentWidth = width;
  }

  @Override
  public LightweightHint show(@NotNull final Editor editor,
                              @NotNull final Point p,
                              final boolean alignToRight,
                              @NotNull final TooltipGroup group,
                              @NotNull final HintHint hintHint) {
    if (myText == null) return null;

    //setup text
    myText = myText.replaceAll(String.valueOf(UIUtil.MNEMONIC), "");
    final boolean expanded = myCurrentWidth > 0 && dressDescription(editor);

    final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    final JComponent contentComponent = editor.getContentComponent();

    final JComponent editorComponent = editor.getComponent();
    if (!editorComponent.isShowing()) return null;
    final JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

    final JEditorPane pane = IdeTooltipManager.initPane(new Html(myText).setKeepFont(true), hintHint, layeredPane);
    hintHint.setContentActive(isActiveHtml(myText));
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

    if (hintHint.isRequestFocus()) {
      pane.setFocusable(true);
    }

    final Ref<AnAction> actionRef = new Ref<>();
    final LightweightHint hint = new LightweightHint(scrollPane) {
      @Override
      public void hide() {
        onHide(pane);
        super.hide();
        final AnAction action = actionRef.get();
        if (action != null) {
          action.unregisterCustomShortcutSet(contentComponent);
        }
      }
    };
    actionRef.set(new AnAction() {
      // an action to expand description when tooltip was shown after mouse move; need to unregister from editor component
      {
        registerCustomShortcutSet(
          new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)),
          contentComponent);
      }

      @Override
      public void actionPerformed(final AnActionEvent e) {
        // The tooltip gets the focus if using a screen reader and invocation through a keyboard shortcut.
        hintHint.setRequestFocus(ScreenReader.isActive() && (e.getInputEvent() instanceof KeyEvent));
        expand(hint, editor, p, pane, alignToRight, group, hintHint);
      }
    });

    pane.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        myActiveLink = true;
        if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
          myActiveLink = false;
          return;
        }
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          final URL url = e.getURL();
          if (url != null) {
            BrowserUtil.browse(url);
            hint.hide();
            return;
          }

          final String description = e.getDescription();
          if (description != null &&
              handle(description, editor)) {
            hint.hide();
            return;
          }

          if (!expanded) {
            expand(hint, editor, p, pane, alignToRight, group, hintHint);
          }
          else {
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
      @Override
      public void mouseReleased(final MouseEvent e) {
        if (!myActiveLink) {
          MouseEvent newMouseEvent = SwingUtilities.convertMouseEvent(e.getComponent(), e, contentComponent);
          hint.hide();
          contentComponent.dispatchEvent(newMouseEvent);
        }
      }

      @Override
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

  private static boolean handle(@NotNull final String ref, @NotNull final Editor editor) {
    // @kirillk please don't remove this call anymore
    return TooltipLinkHandlerEP.handleLink(ref, editor);
  }

  private void expand(@NotNull LightweightHint hint,
                      @NotNull Editor editor,
                      @NotNull Point p,
                      @NotNull JEditorPane pane,
                      boolean alignToRight,
                      @NotNull TooltipGroup group,
                      @NotNull HintHint hintHint) {
    hint.hide();
    if (myCurrentWidth > 0) {
      stripDescription();
    }
    TooltipController.getInstance().showTooltip(editor, new Point(p.x - 3, p.y - 3),
                                                createRenderer(myText, myCurrentWidth > 0 ? 0 : pane.getWidth()), alignToRight, group,
                                                hintHint);
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
    PointerInfo pointerInfo = MouseInfo.getPointerInfo();
    if (pointerInfo == null) return;
    Point mouse = pointerInfo.getLocation();
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

  protected void onHide(JComponent contentComponent) {
  }

  protected LineTooltipRenderer createRenderer(String text, int width) {
    return new LineTooltipRenderer(text, width, getEqualityObjects());
  }

  protected boolean dressDescription(@NotNull final Editor editor) {
    return false;
  }

  protected void stripDescription() {
  }

  static boolean isActiveHtml(String html) {
    return html.contains("</a>");
  }

  public void addBelow(String text) {
    @NonNls String newBody;
    if (myText == null) {
      newBody = UIUtil.getHtmlBody(text);
    }
    else {
      String html1 = UIUtil.getHtmlBody(myText);
      String html2 = UIUtil.getHtmlBody(text);
      newBody = html1 + UIUtil.BORDER_LINE + html2;
    }
    myText = XmlStringUtil.wrapInHtml(newBody);
  }

  public String getText() {
    return myText;
  }
}
