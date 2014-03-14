/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.ex;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public abstract class ComboBoxAction extends AnAction implements CustomComponentAction {
  private static final Icon DISABLED_ARROW_ICON = IconLoader.getDisabledIcon(AllIcons.General.ComboArrow);

  private boolean mySmallVariant = true;
  private DataContext myDataContext;

  protected ComboBoxAction() {
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    JPanel panel = new JPanel(new GridBagLayout());
    ComboBoxButton button = createComboBoxButton(presentation);
    panel.add(button,
              new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 3, 0, 3), 0, 0));
    return panel;
  }

  protected ComboBoxButton createComboBoxButton(Presentation presentation) {
    return new ComboBoxButton(presentation);
  }

  public boolean isSmallVariant() {
    return mySmallVariant;
  }

  public void setSmallVariant(boolean smallVariant) {
    mySmallVariant = smallVariant;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    myDataContext = e.getDataContext();
  }

  @NotNull
  protected abstract DefaultActionGroup createPopupActionGroup(JComponent button);

  protected int getMaxRows() {
    return 30;
  }

  protected int getMinHeight() {
    return 1;
  }

  protected int getMinWidth() {
    return 1;
  }

  protected class ComboBoxButton extends JButton {
    private final Presentation myPresentation;
    private boolean myForcePressed = false;
    private PropertyChangeListener myButtonSynchronizer;
    private boolean myMouseInside = false;
    private JBPopup myPopup;
    private boolean myForceTransparent = false;

    public ComboBoxButton(Presentation presentation) {
      myPresentation = presentation;
      setModel(new MyButtonModel());
      setHorizontalAlignment(LEFT);
      setFocusable(false);
      Insets margins = getMargin();
      setMargin(new Insets(margins.top, 2, margins.bottom, 2));
      if (isSmallVariant()) {
        setBorder(IdeBorderFactory.createEmptyBorder(0, 2, 0, 2));
        if (!UIUtil.isUnderGTKLookAndFeel()) {
          setFont(UIUtil.getLabelFont().deriveFont(11.0f));
        }
      }
      addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (!myForcePressed) {
              IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(new Runnable() {
                @Override
                public void run() {
                  showPopup();
                }
              });
            }
          }
        }
      );

      //noinspection HardCodedStringLiteral
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          myMouseInside = true;
          repaint();
        }

        @Override
        public void mouseExited(MouseEvent e) {
          myMouseInside = false;
          repaint();
        }

        @Override
        public void mousePressed(final MouseEvent e) {
          if (SwingUtilities.isLeftMouseButton(e)) {
            e.consume();
            doClick();
          }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          dispatchEventToPopup(e);
        }
      });
      addMouseMotionListener(new MouseMotionListener() {
        @Override
        public void mouseDragged(MouseEvent e) {
          mouseMoved(new MouseEvent(e.getComponent(),
                                    MouseEvent.MOUSE_MOVED,
                                    e.getWhen(),
                                    e.getModifiers(),
                                    e.getX(),
                                    e.getY(),
                                    e.getClickCount(),
                                    e.isPopupTrigger(),
                                    e.getButton()));
        }

        @Override
        public void mouseMoved(MouseEvent e) {
          dispatchEventToPopup(e);
        }
      });
    }
    // Event forwarding. We need it if user does press-and-drag gesture for opening popup and choosing item there.
    // It works in JComboBox, here we provide the same behavior
    private void dispatchEventToPopup(MouseEvent e) {
      if (myPopup != null && myPopup.isVisible()) {
        JComponent content = myPopup.getContent();
        Rectangle rectangle = content.getBounds();
        Point location = rectangle.getLocation();
        SwingUtilities.convertPointToScreen(location, content);
        Point eventPoint = e.getLocationOnScreen();
        rectangle.setLocation(location);
        if (rectangle.contains(eventPoint)) {
          MouseEvent event = SwingUtilities.convertMouseEvent(e.getComponent(), e, myPopup.getContent());
          Component component = SwingUtilities.getDeepestComponentAt(content, event.getX(), event.getY());
          if (component != null)
            component.dispatchEvent(event);
        }
      }
    }

    public void setForceTransparent(boolean transparent) {
      myForceTransparent = transparent;
    }

    public void showPopup() {
      myForcePressed = true;
      repaint();

      Runnable onDispose = new Runnable() {
        @Override
        public void run() {
          // give button chance to handle action listener
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              myForcePressed = false;
              myPopup = null;
            }
          });
          repaint();
        }
      };

      myPopup = createPopup(onDispose);
      myPopup.show(new RelativePoint(this, new Point(0, getHeight() - 1)));
    }

    @Nullable
    @Override
    public String getToolTipText() {
      return myForcePressed ? null : super.getToolTipText();
    }

    protected JBPopup createPopup(Runnable onDispose) {
      DefaultActionGroup group = createPopupActionGroup(this);

      DataContext context = getDataContext();
      myDataContext = null;
      final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
        null, group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false, onDispose, getMaxRows());
      popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));
      return popup;
    }

    protected DataContext getDataContext() {
      return myDataContext == null ? DataManager.getInstance().getDataContext(this) : myDataContext;
    }

    @Override
    public void removeNotify() {
      if (myButtonSynchronizer != null) {
        myPresentation.removePropertyChangeListener(myButtonSynchronizer);
        myButtonSynchronizer = null;
      }
      super.removeNotify();
    }

    @Override
    public void addNotify() {
      super.addNotify();
      if (myButtonSynchronizer == null) {
        myButtonSynchronizer = new MyButtonSynchronizer();
        myPresentation.addPropertyChangeListener(myButtonSynchronizer);
      }
      initButton();
    }

    private void initButton() {
      setIcon(myPresentation.getIcon());
      setEnabled(myPresentation.isEnabled());
      setText(myPresentation.getText());
      updateTooltipText(myPresentation.getDescription());
      updateButtonSize();
    }

    private void updateTooltipText(String description) {
      String tooltip = KeymapUtil.createTooltipText(description, ComboBoxAction.this);
      setToolTipText(!tooltip.isEmpty() ? tooltip : null);
    }

    @Override
    public void updateUI() {
      super.updateUI();
      if (!UIUtil.isUnderGTKLookAndFeel()) {
        setBorder(UIUtil.getButtonBorder());
      }
    }

    protected class MyButtonModel extends DefaultButtonModel {
      @Override
      public boolean isPressed() {
        return myForcePressed || super.isPressed();
      }

      @Override
      public boolean isArmed() {
        return myForcePressed || super.isArmed();
      }
    }

    private class MyButtonSynchronizer implements PropertyChangeListener {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        if (Presentation.PROP_TEXT.equals(propertyName)) {
          setText((String)evt.getNewValue());
          updateButtonSize();
        }
        else if (Presentation.PROP_DESCRIPTION.equals(propertyName)) {
          updateTooltipText((String)evt.getNewValue());
        }
        else if (Presentation.PROP_ICON.equals(propertyName)) {
          setIcon((Icon)evt.getNewValue());
          updateButtonSize();
        }
        else if (Presentation.PROP_ENABLED.equals(propertyName)) {
          setEnabled(((Boolean)evt.getNewValue()).booleanValue());
        }
      }
    }

    @Override
    public Insets getInsets() {
      final Insets insets = super.getInsets();
      return new Insets(insets.top, insets.left, insets.bottom, insets.right + AllIcons.General.ComboArrow.getIconWidth());
    }

    @Override
    public Insets getInsets(Insets insets) {
      final Insets result = super.getInsets(insets);

      if (UIUtil.isUnderNimbusLookAndFeel() && !isSmallVariant()) {
        result.top += 2;
        result.left += 8;
        result.bottom += 2;
        result.right += 4 + AllIcons.General.ComboArrow.getIconWidth();
      }
      else {
        result.right += AllIcons.General.ComboArrow.getIconWidth();
      }

      return result;
    }

    @Override
    public boolean isOpaque() {
      return !isSmallVariant();
    }

    @Override
    public Dimension getPreferredSize() {
      final boolean isEmpty = getIcon() == null && StringUtil.isEmpty(getText());
      int width = isEmpty ? 10 + AllIcons.General.ComboArrow.getIconWidth() : super.getPreferredSize().width;
      if (isSmallVariant()) width += 4;
      return new Dimension(width, isSmallVariant() ? 19 : UIUtil.isUnderNimbusLookAndFeel() ? 24 : 21);
    }

    @Override
    public void paint(Graphics g) {
      GraphicsUtil.setupAntialiasing(g);
      final Dimension size = getSize();
      final boolean isEmpty = getIcon() == null && StringUtil.isEmpty(getText());

      if (myForceTransparent) {
        final Icon icon = getIcon();
        int x = 7;
        if (icon != null) {
          icon.paintIcon(null, g, x, (size.height - icon.getIconHeight()) / 2);
          x += icon.getIconWidth() + 3;
        }
        if (!StringUtil.isEmpty(getText())) {
          final Font font = getFont();
          g.setFont(font);
          g.setColor(UIManager.getColor("Panel.foreground"));
          g.drawString(getText(), x, (size.height + font.getSize()) / 2 - 1);
        }
      } else {

      if (isSmallVariant()) {
        final Graphics2D g2 = (Graphics2D)g;
        g2.setColor(UIUtil.getControlColor());
        final int w = getWidth();
        final int h = getHeight();
        if (getModel().isArmed() && getModel().isPressed()) {
          g2.setPaint(UIUtil.getGradientPaint(0, 0, UIUtil.getControlColor(), 0, h, ColorUtil.shift(UIUtil.getControlColor(), 0.8)));
        }
        else {
          g2.setPaint(
            UIUtil.getGradientPaint(0, 0, ColorUtil.shift(UIUtil.getControlColor(), 1.1), 0, h, ColorUtil.shift(UIUtil.getControlColor(), 0.9)));
        }
        g2.fillRect(2, 0, w - 2, h);
        GraphicsUtil.setupAntialiasing(g2);
        if (!UIUtil.isUnderDarcula()) {
          if (!myMouseInside) {
            g2.setPaint(UIUtil.getGradientPaint(0, 0, UIUtil.getBorderColor(), 0, h, UIUtil.getBorderColor().darker()));
          } else {
            g2.setPaint(UIUtil.getGradientPaint(0, 0, UIUtil.getBorderColor().darker(), 0, h, UIUtil.getBorderColor().darker().darker()));
          }
        } else {
          if (!myMouseInside) {
            g2.setPaint(UIUtil.getGradientPaint(0, 0, ColorUtil.shift(UIUtil.getControlColor(), 1.2), 0, h, ColorUtil.shift(UIUtil.getControlColor(), 1.3)));
          } else {
            g2.setPaint(UIUtil.getGradientPaint(0, 0, ColorUtil.shift(UIUtil.getControlColor(), 1.4), 0, h, ColorUtil.shift(UIUtil.getControlColor(), 1.5)));
          }
        }

        g2.drawRect(2, 0, w - 3, h - 1);

        final Icon icon = getIcon();
        int x = 7;
        if (icon != null) {
          icon.paintIcon(null, g, x, (size.height - icon.getIconHeight()) / 2);
          x += icon.getIconWidth() + 3;
        }
        if (!StringUtil.isEmpty(getText())) {
          final Font font = getFont();
          g2.setFont(font);
          g2.setColor(UIManager.getColor("Panel.foreground"));
          g2.drawString(getText(), x, (size.height + font.getSize()) / 2 - 1);
        }
      }
      else {
        paintComponent(g);
      }
    }
      final Insets insets = super.getInsets();
      final Icon icon = isEnabled() ? AllIcons.General.ComboArrow : DISABLED_ARROW_ICON;
      final int x;
      if (isEmpty) {
        x = (size.width - icon.getIconWidth()) / 2;
      }
      else {
        if (isSmallVariant()) {
          x = size.width - icon.getIconWidth() - insets.right + 1;
        }
        else {
          x = size.width - icon.getIconWidth() - insets.right + (UIUtil.isUnderNimbusLookAndFeel() ? -3 : 2);
        }
      }

      icon.paintIcon(null, g, x, (size.height - icon.getIconHeight()) / 2);
      g.setPaintMode();
    }

    protected void updateButtonSize() {
      invalidate();
      repaint();
    }
  }
}
