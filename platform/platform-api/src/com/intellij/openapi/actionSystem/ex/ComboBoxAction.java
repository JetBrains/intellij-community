/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public abstract class ComboBoxAction extends AnAction implements CustomComponentAction {
  private static final Icon ARROW_ICON = IconLoader.getIcon("/general/comboArrow.png");
  private static final Icon DISABLED_ARROW_ICON = IconLoader.getDisabledIcon(ARROW_ICON);
  
  private boolean mySmallVariant = false;
  private DataContext myDataContext;

  protected ComboBoxAction() { }

  public void actionPerformed(AnActionEvent e) { }

  public JComponent createCustomComponent(Presentation presentation) {
    JPanel panel = new JPanel(new GridBagLayout());
    ComboBoxButton button = createComboBoxButton(presentation);
    panel.add(button, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 3, 0, 3), 0, 0));
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

    public ComboBoxButton(Presentation presentation) {
      myPresentation = presentation;
      setModel(new MyButtonModel());
      setHorizontalAlignment(LEFT);
      setFocusable(false);
      Insets margins = getMargin();
      setMargin(new Insets(margins.top, 2, margins.bottom, 2));
      if (isSmallVariant()) {
        setBorder(IdeBorderFactory.createEmptyBorder(0));
      }
      addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (!myForcePressed) {
              IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(new Runnable() {
                public void run() {
                  showPopup();
                }
              });
            }
          }
        }
      );

      //noinspection HardCodedStringLiteral
      putClientProperty("Quaqua.Button.style", "placard");
    }

    public void showPopup() {
      myForcePressed = true;
      repaint();

      Runnable onDispose = new Runnable() {
        public void run() {
          // give button chance to handle action listener
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            public void run() {
              myForcePressed = false;
            }
          });
          repaint();
        }
      };

      ListPopup popup = createPopup(onDispose);

      popup.showUnderneathOf(this);
    }

    @Nullable
    @Override
    public String getToolTipText() {
      return myForcePressed ? null : super.getToolTipText();
    }

    protected ListPopup createPopup(Runnable onDispose) {
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
      String tooltip = AnAction.createTooltipText(description, ComboBoxAction.this);
      setToolTipText(tooltip.length() > 0 ? tooltip : null);
    }

    @Override
    public void updateUI() {
      super.updateUI();
      if (UIUtil.isMotifLookAndFeel()) {
        setBorder(BorderFactory.createEtchedBorder());
      }
      else if (!UIUtil.isUnderGTKLookAndFeel()) {
        setBorder(UIUtil.getButtonBorder());
      }
    }

    protected class MyButtonModel extends DefaultButtonModel {
      public boolean isPressed() {
        return myForcePressed || super.isPressed();
      }

      public boolean isArmed() {
        return myForcePressed || super.isArmed();
      }
    }

    private class MyButtonSynchronizer implements PropertyChangeListener {
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
      return new Insets(insets.top, insets.left, insets.bottom, insets.right + ARROW_ICON.getIconWidth());
    }

    @Override
    public Insets getInsets(Insets insets) {
      final Insets result = super.getInsets(insets);

      if (UIUtil.isUnderNimbusLookAndFeel() && !isSmallVariant()) {
        result.top += 2;
        result.left += 8;
        result.bottom += 2;
        result.right += 4 + ARROW_ICON.getIconWidth();
      }
      else {
        result.right += ARROW_ICON.getIconWidth();
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
      int width = isEmpty ? 10 + ARROW_ICON.getIconWidth() : super.getPreferredSize().width;
      if (isSmallVariant()) width += 4;
      return new Dimension(width, isSmallVariant() ? 19 : UIUtil.isUnderNimbusLookAndFeel() ? 24 : 21);
    }

    @Override
    protected void paintComponent(Graphics g) {
      final boolean isEmpty = getIcon() == null && StringUtil.isEmpty(getText());
      final Dimension size = getSize();      
      if (isSmallVariant()) {
        final Graphics2D g2 = (Graphics2D)g;        
        g2.setColor(UIUtil.getControlColor());
        final int w = getWidth();
        final int h = getHeight();
        g2.fillRect(2, 0, w-2, h);
        if (getMousePosition() == null ) {
          g2.setColor(UIUtil.getBorderColor());
        } else {
          g2.setColor(UIUtil.isUnderAquaLookAndFeel() ? new Color(0, 0, 0, 30) : new Color(8, 36, 107));
        }
        g2.drawRect(0,0, w-1, h-1);
        final Icon icon = getIcon();
        int x = 5;
        if (icon != null) {
          icon.paintIcon(null, g, x, (size.height - icon.getIconHeight()) / 2);
          x += icon.getIconWidth() + 3;
        }
        if (!StringUtil.isEmpty(getText())) {
          final Font font = UIUtil.getButtonFont();
          g2.setFont(font);
          g2.setColor(UIManager.getColor("Button.foreground"));
          g2.drawString(getText(), x, (size.height + font.getSize())/2 - 1);
        }
      } else {
        super.paintComponent(g);
      }
        final Insets insets = super.getInsets();
        final Icon icon = isEnabled() ? ARROW_ICON : DISABLED_ARROW_ICON;
        final int x;
        if (isEmpty) {
          x = (size.width - icon.getIconWidth()) / 2;
        } else {
            if (isSmallVariant()) {
              x = size.width - icon.getIconWidth() - insets.right + 1;
            } else {
              x = size.width - icon.getIconWidth() - insets.right + (UIUtil.isUnderNimbusLookAndFeel() ? -3 : 2);
            }
        }
        icon.paintIcon(null, g, x, (size.height - icon.getIconHeight()) / 2);        
    }

    private boolean isGlowSupported() {
      return false;
    }

    protected void updateButtonSize() {
      invalidate();
      repaint();
    }
  }
}
