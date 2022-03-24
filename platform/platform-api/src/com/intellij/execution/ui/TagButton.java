// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.Gray;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.util.function.Consumer;
import java.util.function.Function;

public class TagButton extends JBLayeredPane implements Disposable {
  private static final ColorKey TAG_BACKGROUND = ColorKey.createColorKey("Tag.background",
                                                                           JBUI.CurrentTheme.ActionButton.hoverBackground());
  protected final JButton myButton;
  protected static final int ourInset = JBUI.scale(3);
  private final @Nls String myText;

  public static final Function<JComponent, JComponent> COMPONENT_VALIDATOR_TAG_PROVIDER = e -> ((TagButton)e).myButton;
  protected final InplaceButton myCloseButton;

  public TagButton(@Nls String text, Consumer<AnActionEvent> action) {
    myText = text;
    myButton = new JButton(text) {

      private final Consumer<Color> setBorderColorFunc = (Color c) -> putClientProperty("JButton.borderColor", c);

      @Override
      protected void paintComponent(Graphics g) {
        setBorderColor();
        super.paintComponent(g);
      }

      private void setBorderColor() {
        String outline = ObjectUtils.tryCast(getClientProperty("JComponent.outline"), String.class);

        if (outline != null) {
          if (outline.equals("error")) {
            setBorderColorFunc.accept(JBUI.CurrentTheme.Focus.errorColor(hasFocus()));
            return;
          }
          else if (outline.equals("warning")) {
            setBorderColorFunc.accept(JBUI.CurrentTheme.Focus.warningColor(hasFocus()));
            return;
          }
        }

        setBorderColorFunc.accept(hasFocus() ? null : getBackgroundColor());
      }
    };
    myButton.putClientProperty("styleTag", true);
    myButton.putClientProperty("JButton.backgroundColor", getBackgroundColor());
    myButton.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_BACK_SPACE == e.getKeyCode() || KeyEvent.VK_DELETE == e.getKeyCode()) {
          remove(action, AnActionEvent.createFromInputEvent(e, "", null, DataContext.EMPTY_CONTEXT));
        }
      }
    });
    add(myButton, JLayeredPane.DEFAULT_LAYER);
    myCloseButton = new InplaceButton(new IconButton(null, AllIcons.Actions.Close, AllIcons.Actions.CloseDarkGrey),
                                      a -> remove(action, null));
    myCloseButton.setOpaque(false);
    new HelpTooltip()
      .setTitle(OptionsBundle.message("tag.button.tooltip"))
      .setShortcut(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), null))
      .installOn(myCloseButton);
    add(myCloseButton, JLayeredPane.POPUP_LAYER);

    layoutButtons();
  }

  @Override
  public synchronized void removeFocusListener(FocusListener l) {
    myButton.removeFocusListener(l);
  }

  @Override
  public synchronized void addFocusListener(FocusListener l) {
    myButton.addFocusListener(l);
  }

  @Override
  public synchronized void addMouseListener(MouseListener l) {
    myButton.addMouseListener(l);
  }

  @Override
  public synchronized void removeMouseListener(MouseListener l) {
    myButton.removeMouseListener(l);
  }

  @Override
  public boolean hasFocus() {
    return myButton.hasFocus();
  }

  public void setToolTip(@Nls String toolTip) {
    myButton.setToolTipText(toolTip);
  }

  @Nls
  public String getText() {
    return myText;
  }

  protected void layoutButtons() {
    myButton.setMargin(JBInsets.emptyInsets());
    Dimension size = myButton.getPreferredSize();
    Dimension iconSize = myCloseButton.getPreferredSize();
    Dimension tagSize = new Dimension(size.width + iconSize.width - ourInset * 2, size.height);
    setPreferredSize(tagSize);
    myButton.setBounds(new Rectangle(tagSize));
    myButton.setMargin(JBUI.insetsRight(iconSize.width));
    Point p = new Point(tagSize.width - iconSize.width - ourInset * 3,
                        (tagSize.height - iconSize.height) / 2 + JBUI.scale(1));
    myCloseButton.setBounds(new Rectangle(p, iconSize));
  }

  protected void updateButton(@NlsContexts.Button String text, Icon icon) {
    myButton.setText(text);
    myButton.setIcon(icon);
    layoutButtons();
  }

  private void remove(Consumer<AnActionEvent> action, AnActionEvent e) {
    setVisible(false);
    action.accept(e);
  }

  private static Color getBackgroundColor() {
    return JBColor.namedColor("Tag.background", Gray.xDF);
  }

  @Override
  public void dispose() {
  }
}
