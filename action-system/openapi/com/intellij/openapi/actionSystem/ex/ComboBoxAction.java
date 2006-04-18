/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.jetbrains.annotations.NotNull;

public abstract class ComboBoxAction extends AnAction implements CustomComponentAction {
  private static final Icon ARROW_ICON = IconLoader.getIcon("/general/comboArrow.png");
  private final String myActionPlace;

  protected ComboBoxAction(String dropDownPlace) {
    myActionPlace = dropDownPlace;
  }

  protected ComboBoxAction() {
    this(ActionPlaces.UNKNOWN);
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

  protected class ComboBoxButton extends JButton {
    private Presentation myPresentation;
    private boolean myForcePressed = false;
    private PropertyChangeListener myButtonSynchronizer;

    public ComboBoxButton(Presentation presentation) {
      myPresentation = presentation;
      setModel(new MyButtonModel());
      setHorizontalAlignment(JButton.LEFT);
      setFocusable(false);
      Insets margins = getMargin();
      setMargin(new Insets(margins.top, 2, margins.bottom, 2));
      addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            showPopup();
          }
        }
      );
    }

    public void showPopup() {
      DefaultActionGroup group = createPopupActionGroup(this);
      ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(myActionPlace, group);
      myForcePressed = true;
      JPopupMenu menuComponent = menu.getComponent();
      menuComponent.addPopupMenuListener(
        new PopupMenuListener() {
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}

          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            myForcePressed = false;
            repaint();
          }

          public void popupMenuCanceled(PopupMenuEvent e) {}
        }
      );
      repaint();
      menuComponent.show(this, 0, getHeight());
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
        } else if (Presentation.PROP_DESCRIPTION.equals(propertyName)) {
          updateTooltipText((String)evt.getNewValue());
        } else if (Presentation.PROP_ICON.equals(propertyName)) {
          setIcon((Icon)evt.getNewValue());
          updateButtonSize();
        } else if (Presentation.PROP_ENABLED.equals(propertyName)) {
          setEnabled(((Boolean)evt.getNewValue()).booleanValue());
        }
      }
    }

    public final void paint(Graphics g) {
      super.paint(g);
      Dimension size = getSize();
      String text = getText();
      boolean isEmpty = getIcon() == null && (text == null || text.trim().length() == 0);
      int x = isEmpty ? (size.width - ARROW_ICON.getIconWidth())/2 : size.width - ARROW_ICON.getIconWidth() - 2;
      ARROW_ICON.paintIcon(null, g, x, (size.height - ARROW_ICON.getIconHeight()) / 2);
    }

    protected void updateButtonSize() {
      int width;
      String text = getText();
      if ((text == null || text.trim().length() == 0) && (getIcon() == null)) {
        width = ARROW_ICON.getIconWidth() + 10;
      } else {
        width = getUI().getPreferredSize(this).width + ARROW_ICON.getIconWidth() + 2;
      }
      setPreferredSize(new Dimension(width, 21));
    }
  }
}
