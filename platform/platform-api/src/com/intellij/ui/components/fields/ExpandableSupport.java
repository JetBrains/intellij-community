// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.fields;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.Expandable;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.fields.ExtendableTextComponent.Extension;
import com.intellij.util.Function;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.AncestorListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.intellij.openapi.keymap.KeymapUtil.createTooltipText;
import static java.awt.event.InputEvent.CTRL_MASK;
import static java.beans.EventHandler.create;
import static java.util.Collections.singletonList;
import static javax.swing.KeyStroke.getKeyStroke;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;

@Experimental
public abstract class ExpandableSupport<Source extends JComponent> implements Expandable {
  private static final int MINIMAL_WIDTH = 50;
  private final Source source;
  private final Function<String, String> onShow;
  private final Function<String, String> onHide;
  private JBPopup popup;
  private String title;
  private String comment;

  public ExpandableSupport(@NotNull Source source, Function<String, String> onShow, Function<String, String> onHide) {
    this.source = source;
    this.onShow = onShow != null ? onShow : Function.ID;
    this.onHide = onHide != null ? onHide : Function.ID;
    source.putClientProperty(Expandable.class, this);
    source.addAncestorListener(create(AncestorListener.class, this, "collapse"));
    source.addComponentListener(create(ComponentListener.class, this, "collapse"));
  }

  protected abstract boolean isEditable(@NotNull Source source);

  protected abstract int getCaretPosition(@NotNull Source source);

  protected abstract String getText(@NotNull Source source);

  protected abstract void setText(@NotNull Source source, String text, int caret);

  protected abstract Insets getInsets(@NotNull Source source);

  public final String getTitle() {
    return title;
  }

  public final void setTitle(String title) {
    this.title = title;
  }

  public final String getComment() {
    return comment;
  }

  public final void setComment(String comment) {
    this.comment = comment;
  }

  @Override
  public final boolean isExpanded() {
    return popup != null;
  }

  @Override
  public final void collapse() {
    if (popup != null) popup.cancel();
  }

  @Override
  public final void expand() {
    if (popup != null || !source.isEnabled()) return;

    Font font = source.getFont();
    FontMetrics metrics = font == null ? null : source.getFontMetrics(font);
    int height = metrics == null ? 16 : metrics.getHeight();
    Dimension size = new Dimension(height * 32, height * 16);

    JTextArea area = new JTextArea(onShow.fun(getText(source)));
    area.putClientProperty(Expandable.class, this);
    area.setEditable(isEditable(source));
    area.setBackground(source.getBackground());
    area.setForeground(source.getForeground());
    area.setFont(font);
    area.setWrapStyleWord(true);
    area.setLineWrap(true);
    setCaretPositionSafely(area, getCaretPosition(source));
    UIUtil.addUndoRedoActions(area);

    JBScrollPane pane = new JBScrollPane(area);
    addExtension(pane, createCollapseExtension(this));

    Insets insets = getInsets(source);
    //TODO: support scroll pane

    JBInsets.addTo(size, insets);
    JBInsets.addTo(size, pane.getInsets());
    if (size.width - MINIMAL_WIDTH < source.getWidth()) size.width = source.getWidth();

    Point location = new Point(0, 0);
    SwingUtilities.convertPointToScreen(location, source);
    Rectangle screen = ScreenUtil.getScreenRectangle(source);
    int bottom = screen.y - location.y + screen.height;
    if (bottom < size.height) {
      int top = location.y - screen.y + source.getHeight();
      if (top < bottom) {
        size.height = bottom;
      }
      else {
        if (size.height > top) size.height = top;
        location.y -= size.height - source.getHeight();
      }
    }
    pane.setPreferredSize(size);
    pane.setViewportBorder(BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right));

    popup = JBPopupFactory
      .getInstance()
      .createComponentPopupBuilder(pane, area)
      .setMayBeParent(true) // this creates a popup as a dialog with alwaysOnTop=false
      .setFocusable(true)
      .setRequestFocus(true)
      .setTitle(title)
      .setAdText(comment)
      .setLocateByContent(true)
      .setCancelOnWindowDeactivation(false)
      .setKeyboardActions(singletonList(Pair.create(event -> {
        collapse();
        Window window = UIUtil.getWindow(source);
        if (window != null) {
          window.dispatchEvent(
            new KeyEvent(source, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), CTRL_MASK, KeyEvent.VK_ENTER, '\r'));
        }
      }, getKeyStroke(KeyEvent.VK_ENTER, CTRL_MASK))))
      .setCancelCallback(() -> {
        try {
          if (isEditable(source)) {
            setText(source, onHide.fun(area.getText()), area.getCaretPosition());
          }
          popup = null;
          return true;
        }
        catch (Exception ignore) {
          return false;
        }
      }).createPopup();
    popup.show(new RelativePoint(location));
  }

  private static void setCaretPositionSafely(JTextComponent component, int caret) {
    try {
      component.setCaretPosition(caret);
    }
    catch (Exception ignored) {
    }
  }

  public static Extension createCollapseExtension(@NotNull Expandable expandable) {
    return Extension.create(AllIcons.General.CollapseComponent,
                            AllIcons.General.CollapseComponentHover,
                            createTooltipText("Collapse", "CollapseExpandableComponent"),
                            expandable::collapse);
  }

  public static Extension createExpandExtension(@NotNull Expandable expandable) {
    return Extension.create(AllIcons.General.ExpandComponent,
                            AllIcons.General.ExpandComponentHover,
                            createTooltipText("Expand", "ExpandExpandableComponent"),
                            expandable::collapse);
  }

  private static void addExtension(@NotNull JScrollPane pane, @NotNull Extension extension) {
    pane.setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_ALWAYS);
    JScrollBar vsb = pane.getVerticalScrollBar();
    if (vsb != null) {
      vsb.add(JBScrollBar.LEADING, new JLabel(extension.getIcon(false)) {{
        setToolTipText(extension.getTooltip());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(JBUI.Borders.empty(5, 0, 5, 5));
        addMouseListener(new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent event) {
            setIcon(extension.getIcon(true));
          }

          @Override
          public void mouseExited(MouseEvent event) {
            setIcon(extension.getIcon(false));
          }

          @Override
          public void mousePressed(MouseEvent event) {
            Runnable action = extension.getActionOnClick();
            if (action != null) action.run();
          }
        });
      }});
      JViewport viewport = pane.getViewport();
      if (viewport != null) {
        Component view = viewport.getView();
        if (view != null) vsb.setBackground(view.getBackground());
      }
    }
  }


  static class TextComponent<Source extends JTextComponent> extends ExpandableSupport<Source> {
    TextComponent(@NotNull Source source, Function<String, String> onShow, Function<String, String> onHide) {
      super(source, onShow, onHide);
    }

    @Override
    protected boolean isEditable(@NotNull Source source) {
      return source.isEditable();
    }

    @Override
    protected int getCaretPosition(@NotNull Source source) {
      return source.getCaretPosition();
    }

    @Override
    protected String getText(@NotNull Source source) {
      return source.getText();
    }

    @Override
    protected void setText(@NotNull Source source, String text, int caret) {
      source.setText(text);
      setCaretPositionSafely(source, caret);
    }

    @Override
    protected Insets getInsets(@NotNull Source source) {
      Insets insets = source.getInsets();
      Insets margin = source.getMargin();
      if (margin != null) {
        insets.top += margin.top;
        insets.left += margin.left;
        insets.right += margin.right;
        insets.bottom += margin.bottom;
      }
      return insets;
    }
  }

  static final class Area extends TextComponent<JTextArea> {
    Area(@NotNull JTextArea area, Function<String, String> onShow, Function<String, String> onHide) {
      super(area, onShow, onHide);
    }

    @Override
    protected void setText(@NotNull JTextArea source, String text, int caret) {
      super.setText(source, text, 0);
    }

    @Override
    protected Insets getInsets(@NotNull JTextArea source) {
      //noinspection UnnecessaryLocalVariable //TODO: support scroll pane
      Insets insets = super.getInsets(source);
      return insets;
    }
  }
}
