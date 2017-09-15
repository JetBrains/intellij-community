/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public abstract class ComboBoxAction extends AnAction implements CustomComponentAction {
  private static Icon myIcon = null;
  private static Icon myDisabledIcon = null;

  public static Icon getArrowIcon(boolean enabled) {
    if (UIUtil.isUnderWin10LookAndFeel()) {
      return IconLoader.getIcon("/com/intellij/ide/ui/laf/icons/win10/comboDropTriangle.png");
    }
    Icon icon = UIUtil.isUnderDarcula() ? AllIcons.General.ComboArrow : AllIcons.General.ComboBoxButtonArrow;
    if (myIcon != icon) {
      myIcon = icon;
      myDisabledIcon = IconLoader.getDisabledIcon(myIcon);
    }
    return enabled ? myIcon : myDisabledIcon;
  }

  private boolean mySmallVariant = true;
  private String myPopupTitle;

  protected ComboBoxAction() {
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    JFrame frame = WindowManager.getInstance().getFrame(project);
    if (!(frame instanceof IdeFrame)) return;

    ListPopup popup = createActionPopup(e.getDataContext(), ((IdeFrame)frame).getComponent(), null);
    popup.showCenteredInCurrentWindow(project);
  }

  @NotNull
  private ListPopup createActionPopup(@NotNull DataContext context, @NotNull JComponent component, @Nullable Runnable disposeCallback) {
    DefaultActionGroup group = createPopupActionGroup(component, context);
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      myPopupTitle, group, context, false, shouldShowDisabledActions(), false, disposeCallback, getMaxRows(), getPreselectCondition());
    popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));
    return popup;
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    JPanel panel = new JPanel(new GridBagLayout());
    ComboBoxButton button = createComboBoxButton(presentation);
    panel.add(button,
              new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.insets(0, 3, 0, 3), 0, 0));
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

  public void setPopupTitle(String popupTitle) {
    myPopupTitle = popupTitle;
  }

  @Override
  public void update(AnActionEvent e) {
  }

  protected boolean shouldShowDisabledActions() {
    return false;
  }

  @NotNull
  protected abstract DefaultActionGroup createPopupActionGroup(JComponent button);

  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button, @NotNull  DataContext dataContext) {
    return createPopupActionGroup(button);
  }

  protected int getMaxRows() {
    return 30;
  }

  protected int getMinHeight() {
    return 1;
  }

  protected int getMinWidth() {
    return 1;
  }

  protected class ComboBoxButton extends JButton implements UserActivityProviderComponent {
    private final Presentation myPresentation;
    private boolean myForcePressed = false;
    private PropertyChangeListener myButtonSynchronizer;
    private boolean myMouseInside = false;
    private JBPopup myPopup;
    private boolean myForceTransparent = false;

    public ComboBoxButton(Presentation presentation) {
      myPresentation = presentation;
      setModel(new MyButtonModel());
      getModel().setEnabled(myPresentation.isEnabled());
      setVisible(presentation.isVisible());
      setHorizontalAlignment(LEFT);
      setFocusable(false);
      putClientProperty("styleCombo", Boolean.TRUE);
      Insets margins = getMargin();
      setMargin(JBUI.insets(margins.top, 2, margins.bottom, 2));
      if (isSmallVariant()) {
        if (!UIUtil.isUnderWin10LookAndFeel()) {
          setBorder(JBUI.Borders.empty(0, 2));
        }

        if (!UIUtil.isUnderGTKLookAndFeel()) {
          setFont(JBUI.Fonts.label(11));
        }
      }
      addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (!myForcePressed) {
              IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> showPopup());
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
          mouseMoved(MouseEventAdapter.convert(e, e.getComponent(),
                                               MouseEvent.MOUSE_MOVED,
                                               e.getWhen(),
                                               e.getModifiers() | e.getModifiersEx(),
                                               e.getX(),
                                               e.getY()));
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

    @NotNull
    private Runnable setForcePressed() {
      myForcePressed = true;
      repaint();

      return () -> {
        // give the button a chance to handle action listener
        ApplicationManager.getApplication().invokeLater(() -> {
          myForcePressed = false;
          myPopup = null;
          repaint();
        }, ModalityState.any());
        repaint();
        fireStateChanged();
      };
    }

    @Nullable
    @Override
    public String getToolTipText() {
      return myForcePressed ? null : super.getToolTipText();
    }

    public void showPopup() {
      createPopup(setForcePressed()).showUnderneathOf(this);
    }

    protected JBPopup createPopup(Runnable onDispose) {
      return createActionPopup(getDataContext(), this, onDispose);
    }

    private ComboBoxAction getMyAction() {
      return ComboBoxAction.this;
    }

    protected DataContext getDataContext() {
      return DataManager.getInstance().getDataContext(this);
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
      //if (!UIUtil.isUnderGTKLookAndFeel()) {
      //  setBorder(UIUtil.getButtonBorder());
      //}
      //((JComponent)getParent().getParent()).revalidate();
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
      insets.right += getArrowIcon(isEnabled()).getIconWidth();
      return insets;
    }

    @Override
    public Insets getInsets(Insets insets) {
      final Insets result = super.getInsets(insets);
      result.right += getArrowIcon(isEnabled()).getIconWidth();
      return result;
    }

    @Override
    public boolean isOpaque() {
      return !isSmallVariant();
    }

    @Override
    public Dimension getPreferredSize() {
      final boolean isEmpty = getIcon() == null && StringUtil.isEmpty(getText());
      int width = isEmpty ? JBUI.scale(10) + getArrowIcon(isEnabled()).getIconWidth() : super.getPreferredSize().width;
      if (isSmallVariant() && !UIUtil.isUnderDefaultMacTheme()) {
        width += JBUI.scale(4);
        if (UIUtil.isUnderWin10LookAndFeel()) {
          width += JBUI.scale(8);
        }
      }

      int height = UIUtil.isUnderWin10LookAndFeel() ? JBUI.scale(24) : JBUI.scale(19);
      return new Dimension(width, isSmallVariant() ? height : super.getPreferredSize().height);
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(super.getMinimumSize().width, getPreferredSize().height);
    }

    @Override
    public Font getFont() {
      return SystemInfo.isMac && isSmallVariant() ? UIUtil.getLabelFont(UIUtil.FontSize.SMALL) : UIUtil.getLabelFont();
    }

    @Override
    public void paint(Graphics g) {
      Dimension size = getSize();

      if (UIUtil.isUnderDefaultMacTheme() || UIUtil.isUnderWin10LookAndFeel()) {
        super.paint(g);
      } else {
        UISettings.setupAntialiasing(g);
        GraphicsUtil.setupRoundedBorderAntialiasing(g);

        final Color textColor = isEnabled()
                                ? UIManager.getColor("Panel.foreground")
                                : UIUtil.getInactiveTextColor();

        if (myForceTransparent) {
          final Icon icon = getIcon();
          int x = 7;
          if (icon != null) {
            icon.paintIcon(this, g, x, (size.height - icon.getIconHeight()) / 2);
            x += icon.getIconWidth() + 3;
          }
          if (!StringUtil.isEmpty(getText())) {
            final Font font = getFont();
            g.setFont(font);
            g.setColor(textColor);
            UIUtil.drawCenteredString((Graphics2D)g, new Rectangle(x, 0, Integer.MAX_VALUE, size.height), getText(), false, true);
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
              if (UIUtil.isUnderDarcula()) {
                g2.setPaint(UIUtil.getGradientPaint(0, 0, ColorUtil.shift(UIUtil.getControlColor(), 1.1), 0, h,
                                                    ColorUtil.shift(UIUtil.getControlColor(), 0.9)));
              }
              else {
                g2.setPaint(UIUtil.getGradientPaint(0, 0, new JBColor(SystemInfo.isMac ? Gray._226 : Gray._245, Gray._131), 0, h,
                                                    new JBColor(SystemInfo.isMac ? Gray._198 : Gray._208, Gray._128)));
              }
            }

            g2.fillRoundRect(2, 0, w - 2, h, 5, 5);

            Color borderColor = myMouseInside ? new JBColor(Gray._111, Gray._118) : new JBColor(Gray._151, Gray._95);
            g2.setPaint(borderColor);
            g2.drawRoundRect(2, 0, w - 3, h - 1, 5, 5);

            final Icon icon = getIcon();
            int x = 7;
            if (icon != null) {
              icon.paintIcon(this, g, x, (size.height - icon.getIconHeight()) / 2);
              x += icon.getIconWidth() + 3;
            }
            if (!StringUtil.isEmpty(getText())) {
              final Font font = getFont();
              g2.setFont(font);
              g2.setColor(textColor);
              UIUtil.drawCenteredString(g2, new Rectangle(x, 0, Integer.MAX_VALUE, size.height), getText(), false, true);
            }
          }
          else {
            super.paint(g);
          }
        }
      }

      Insets insets = super.getInsets();
      Icon icon = getArrowIcon(isEnabled());

      int x = size.width - icon.getIconWidth();
      if (UIUtil.isUnderWin10LookAndFeel()) {
        x -= JBUI.scale(6);
        x -= JBUI.scale(UIUtil.getParentOfType(ActionToolbar.class, this) != null ? 2 : 0);
      }
      else {
        x -= insets.right;

        if (isSmallVariant()) {
          x += JBUI.scale(1);

          if (UIUtil.isUnderDefaultMacTheme()) {
            x -= JBUI.scale(3);
          }
        }
        else {
          x += JBUI.scale(UIUtil.isUnderNimbusLookAndFeel() ? -3 : 2);
        }
      }

      icon.paintIcon(null, g, x, (size.height - icon.getIconHeight()) / 2);
      g.setPaintMode();
    }

    protected void updateButtonSize() {
      invalidate();
      repaint();
      setSize(getPreferredSize());
      repaint();
    }
  }

  protected Condition<AnAction> getPreselectCondition() { return null; }
}
