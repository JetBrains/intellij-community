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
import com.intellij.ide.TooltipEvent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.Html;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import com.intellij.util.ui.update.ComparableObject;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.ArrayList;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

/**
 * @author cdr
 */
public class LineTooltipRenderer extends ComparableObject.Impl implements TooltipRenderer {

  @NonNls @Nullable protected String myText;

  //is used for suppressing some events while processing links  
  private volatile boolean myActiveLink = false;
  protected final int myCurrentWidth;

  protected interface TooltipReloader {
    void reload(boolean toExpand);
  }

  public LineTooltipRenderer(@Nullable String text, @NotNull Object[] comparable) {
    this(text, 0, comparable);
  }

  public LineTooltipRenderer(@Nullable final String text, final int width, @NotNull Object[] comparable) {
    super(comparable);
    myCurrentWidth = width;
    myText = text;
  }

  @NotNull
  protected JPanel createMainPanel(@NotNull final HintHint hintHint,
                                   @NotNull JComponent pane) {
    JPanel grid = new JPanel(new GridBagLayout());
    GridBag bag = new GridBag()
      .anchor(GridBagConstraints.CENTER)
      .fillCellHorizontally();
    
    grid.add(pane, bag);
    grid.setBackground(hintHint.getTextBackground());
    grid.setBorder(JBUI.Borders.empty());

    return grid;
  }

  @Override
  public LightweightHint show(@NotNull final Editor editor,
                              @NotNull final Point p,
                              final boolean alignToRight,
                              @NotNull final TooltipGroup group,
                              @NotNull final HintHint hintHint) {
    if (myText == null) return null;

    //setup text
    String tooltipPreText = myText.replaceAll(String.valueOf(UIUtil.MNEMONIC), "");
    String dressedText = dressDescription(editor, tooltipPreText, myCurrentWidth > 0);

    final boolean expanded = myCurrentWidth > 0 && !dressedText.equals(tooltipPreText);

    final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    final JComponent contentComponent = editor.getContentComponent();

    final JComponent editorComponent = editor.getComponent();
    if (!editorComponent.isShowing()) return null;
    final JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

    JEditorPane editorPane = IdeTooltipManager.initPane(new Html(dressedText).setKeepFont(true), hintHint, layeredPane);
    hintHint.setContentActive(isActiveHtml(dressedText));
    if (!hintHint.isAwtTooltip()) {
      correctLocation(editor, editorPane, p, alignToRight, expanded, myCurrentWidth);
    }

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(editorPane);
    scrollPane.setBorder(null);

    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

    scrollPane.setOpaque(hintHint.isOpaqueAllowed());
    scrollPane.getViewport().setOpaque(hintHint.isOpaqueAllowed());

    scrollPane.setBackground(hintHint.getTextBackground());
    scrollPane.getViewport().setBackground(hintHint.getTextBackground());

    scrollPane.setViewportBorder(null);

    if (hintHint.isRequestFocus()) {
      editorPane.setFocusable(true);
    }

    ArrayList<AnAction> actions = ContainerUtil.newArrayList();
    JPanel component = createMainPanel(hintHint, scrollPane);
    final LightweightHint hint = new LightweightHint(component) {

      @Override
      public void hide() {
        onHide(editorPane);
        super.hide();
        for (AnAction action : actions) {
          action.unregisterCustomShortcutSet(contentComponent);
        }
      }

      @Override
      protected boolean canAutoHideOn(TooltipEvent event) {
        if (!LineTooltipRenderer.this.canAutoHideOn(event)) {
          return false;
        }

        return super.canAutoHideOn(event);
      }
    };


    TooltipReloader reloader = (toExpand) -> reloadFor(hint, editor, p, editorPane, alignToRight, group, hintHint, toExpand);

    actions.add(new AnAction() {
      // an action to expand description when tooltip was shown after mouse move; need to unregister from editor component
      {
        registerCustomShortcutSet(getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION), contentComponent);
      }

      @Override
      public void actionPerformed(final AnActionEvent e) {
        // The tooltip gets the focus if using a screen reader and invocation through a keyboard shortcut.
        hintHint.setRequestFocus(ScreenReader.isActive() && (e.getInputEvent() instanceof KeyEvent));
        reloader.reload(!expanded);
      }
    });

    editorPane.addHyperlinkListener(new HyperlinkListener() {
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

          reloader.reload(!expanded);
        }
      }
    });

    fillPanel(editor, component, hint, hintHint, actions, reloader);

    component.addMouseListener(new MouseAdapter() {

      // This listener makes hint transparent for mouse events. It means that hint is closed
      // by MousePressed and this MousePressed goes into the underlying editor component.
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
        if (component.getBounds().contains(e.getPoint())) {
          return;
        }

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

  protected boolean canAutoHideOn(@NotNull TooltipEvent event) {
    return true;
  }

  private void reloadFor(@NotNull LightweightHint hint,
                         @NotNull Editor editor,
                         @NotNull Point p,
                         @NotNull JComponent pane,
                         boolean alignToRight,
                         @NotNull TooltipGroup group,
                         @NotNull HintHint hintHint,
                         boolean expand) {
    //required for immediately showing. Otherwise there are several concurrent issues
    hint.hide();

    hintHint.setShowImmediately(true);
    TooltipController.getInstance().showTooltip(editor, new Point(p.x - 3, p.y - 3),
                                                createRenderer(myText, expand ? pane.getWidth() : 0), alignToRight, group,
                                                hintHint);
  }

  protected void fillPanel(@NotNull Editor editor,
                           @NotNull JPanel component,
                           @NotNull LightweightHint hint,
                           @NotNull HintHint hintHint,
                           @NotNull ArrayList<AnAction> actions,
                           @NotNull TooltipReloader expandCallback) {

  }

  private static boolean handle(@NotNull final String ref, @NotNull final Editor editor) {
    // @kirillk please don't remove this call anymore
    return TooltipLinkHandlerEP.handleLink(ref, editor);
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

  protected void onHide(@NotNull JComponent contentComponent) {
  }

  protected LineTooltipRenderer createRenderer(@Nullable String text, int width) {
    return new LineTooltipRenderer(text, width, getEqualityObjects());
  }

  @NotNull
  protected String dressDescription(@NotNull final Editor editor, @NotNull String tooltipText, boolean expanded) {
    return tooltipText;
  }

  protected static boolean isActiveHtml(@NotNull String html) {
    return html.contains("</a>");
  }

  public void addBelow(@NotNull String text) {
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

  @Nullable
  public String getText() {
    return myText;
  }
}
