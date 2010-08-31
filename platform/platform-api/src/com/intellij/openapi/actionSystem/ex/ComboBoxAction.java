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
package com.intellij.openapi.actionSystem.ex;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public abstract class ComboBoxAction extends AnAction implements CustomComponentAction {
  private static final Icon ARROW_ICON = IconLoader.getIcon("/general/comboArrow.png");
  private static final Icon DISABLED_ARROW_ICON = IconLoader.getDisabledIcon(ARROW_ICON);

  protected ComboBoxAction() {
  }

  public void actionPerformed(AnActionEvent e) {}

  public JComponent createCustomComponent(Presentation presentation) {
    JPanel panel=new JPanel(new GridBagLayout());
    ComboBoxButton button = new ComboBoxButton(presentation);
    panel.add(button,
              new GridBagConstraints(0,0,1,1,1,1,GridBagConstraints.CENTER,GridBagConstraints.BOTH,new Insets(0,3,0,3),0,0)
    );
    return panel;
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
          SwingUtilities.invokeLater(new Runnable() {
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

    protected ListPopup createPopup(Runnable onDispose) {
      DefaultActionGroup group = createPopupActionGroup(this);
      final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, group, DataManager.getInstance().getDataContext(),
                                                                                  JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false,
                                                                                  onDispose,
                                                                                  getMaxRows());
      popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));
      return popup;
    }

    public void removeNotify() {
      if (myButtonSynchronizer != null) {
        myPresentation.removePropertyChangeListener(myButtonSynchronizer);
        myButtonSynchronizer = null;
      }
      super.removeNotify();
    }

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

    public void updateUI() {
      super.updateUI();
      if(UIUtil.isMotifLookAndFeel()){
        setBorder(BorderFactory.createEtchedBorder());
      }else{
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

      if (UIUtil.isUnderNimbusLookAndFeel()) {
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
    public Dimension getPreferredSize() {
      int width = super.getPreferredSize().width;

      final String text = getText();
      if ((text == null || text.trim().length() == 0) && getIcon() == null) {
        width = 10 + ARROW_ICON.getIconWidth();
      }

      return new Dimension(width, UIUtil.isUnderNimbusLookAndFeel() ? 24 : 21);
    }

    public final void paint(Graphics g) {
      super.paint(g);
      Dimension size = getSize();
      String text = getText();
      boolean isEmpty = getIcon() == null && (text == null || text.trim().length() == 0);
      final Insets insets = super.getInsets();
      final Icon icon = isEnabled() ? ARROW_ICON : DISABLED_ARROW_ICON;
      int x = isEmpty ? (size.width - icon.getIconWidth())/2: size.width - icon.getIconWidth() - insets.right + (UIUtil.isUnderNimbusLookAndFeel() ? -3 : 2);
      icon.paintIcon(null, g, x, (size.height - icon.getIconHeight()) / 2);
    }

    protected void updateButtonSize() {
      invalidate();
      repaint();
    }
  }
}
