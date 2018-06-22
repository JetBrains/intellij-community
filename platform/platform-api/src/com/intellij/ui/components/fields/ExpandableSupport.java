// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.fields;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.Expandable;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.fields.ExtendableTextComponent.Extension;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.AncestorListener;
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

/**
 * @author Sergey Malenkov
 */
@Experimental
public abstract class ExpandableSupport<Source extends JComponent> implements Expandable {
  private static final int MINIMAL_WIDTH = 50;
  private final Source source;
  private final Function<? super String, String> onShow;
  private final Function<? super String, String> onHide;
  private JBPopup popup;
  private String title;
  private String comment;

  public ExpandableSupport(@NotNull Source source, Function<? super String, String> onShow, Function<? super String, String> onHide) {
    this.source = source;
    this.onShow = onShow != null ? onShow : Function.ID;
    this.onHide = onHide != null ? onHide : Function.ID;
    source.putClientProperty(Expandable.class, this);
    source.addAncestorListener(create(AncestorListener.class, this, "collapse"));
    source.addComponentListener(create(ComponentListener.class, this, "collapse"));
  }

  /**
   * @param source the source expandable component covered by the popup
   * @param onShow a string converter from the source to the popup content
   * @return a specific content to create the popup
   */
  @NotNull
  protected abstract Content prepare(@NotNull Source source, @NotNull Function<? super String, String> onShow);

  protected interface Content {
    /**
     * @return a component to show on the popup
     */
    @NotNull
    JComponent getContentComponent();

    /**
     * @return a component to focus on after showing the popup
     */
    JComponent getFocusableComponent();

    /**
     * This method is called after closing the popup.
     *
     * @param onHide a string converter from the popup content to the source
     */
    void cancel(@NotNull Function<? super String, String> onHide);
  }

  /**
   * @return a text from the popup's header or {@code null} if header is hidden.
   */
  public final String getTitle() {
    return title;
  }

  /**
   * @param title a text for the popup's header or {@code null} if header is not needed
   */
  public final void setTitle(String title) {
    this.title = title;
  }

  /**
   * @return a text from the popup's footer or {@code null} if footer is hidden.
   */
  public final String getComment() {
    return comment;
  }

  /**
   * @param comment a text for the popup's footer or {@code null} if footer is not needed
   */
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

    Content content = prepare(source, onShow);
    JComponent component = content.getContentComponent();
    Dimension size = component.getPreferredSize();
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
    component.setPreferredSize(size);

    popup = JBPopupFactory
      .getInstance()
      .createComponentPopupBuilder(component, content.getFocusableComponent())
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
          content.cancel(onHide);
          popup = null;
          return true;
        }
        catch (Exception ignore) {
          return false;
        }
      }).createPopup();
    popup.show(new RelativePoint(location));
  }

  @NotNull
  public Extension createCollapseExtension() {
    return Extension.create(AllIcons.General.CollapseComponent,
                            AllIcons.General.CollapseComponentHover,
                            createTooltipText("Collapse", "CollapseExpandableComponent"),
                            this::collapse);
  }

  @NotNull
  public Extension createExpandExtension() {
    return Extension.create(AllIcons.General.ExpandComponent,
                            AllIcons.General.ExpandComponentHover,
                            createTooltipText("Expand", "ExpandExpandableComponent"),
                            this::expand);
  }

  @NotNull
  public static JLabel createLabel(@NotNull Extension extension) {
    return new JLabel(extension.getIcon(false)) {{
      setToolTipText(extension.getTooltip());
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
    }};
  }
}
