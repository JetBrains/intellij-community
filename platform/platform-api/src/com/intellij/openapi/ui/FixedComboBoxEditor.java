/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.Gray;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;

/**
 * Use this editor if you wish your combobox editor to look good on Macs.
 *
 * User: spLeaner
 */
public class FixedComboBoxEditor implements ComboBoxEditor {
  public static final Border EDITOR_BORDER = new MacComboBoxEditorBorder(false);
  public static final Border DISABLED_EDITOR_BORDER = new MacComboBoxEditorBorder(true);

  @NotNull private final JBTextField myField;
  private Object oldValue;

  public FixedComboBoxEditor() {
    if (SystemInfo.isMac && (UIUtil.isUnderAquaLookAndFeel() || UIUtil.isUnderIntelliJLaF())) {
      myField = new MacComboBoxTextField();
    }
    else {
      myField = new JBTextField();
      myField.setBorder(null);
    }
  }

  @NotNull
  public JBTextField getField() {
    return myField;
  }

  @Override
  public Component getEditorComponent() {
    return myField;
  }

  public void setItem(Object anObject) {
    if (anObject != null) {
      myField.setText(anObject.toString());
      oldValue = anObject;
    }
    else {
      myField.setText("");
    }
  }

  public Object getItem() {
    Object newValue = myField.getText();
    if (oldValue != null && !(oldValue instanceof String)) {
      // The original value is not a string. Should return the value in it's
      // original type.
      if (newValue.equals(oldValue.toString())) {
        return oldValue;
      }
      else {
        // Must take the value from the editor and get the value and cast it to the new type.
        Class cls = oldValue.getClass();
        try {
          Method method = cls.getMethod("valueOf", new Class[]{String.class});
          newValue = method.invoke(oldValue, new Object[]{myField.getText()});
        }
        catch (Exception ex) {
          // Fail silently and return the newValue (a String object)
        }
      }
    }
    return newValue;
  }

  public void selectAll() {
    myField.selectAll();
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
      IdeFocusManager.getGlobalInstance().requestFocus(myField, true);
    });
  }

  @Override
  public void addActionListener(ActionListener l) {
  }

  @Override
  public void removeActionListener(ActionListener l) {
  }

  @Nullable
  private static ComboPopup getComboboxPopup(final JComboBox comboBox) {
    final ComboBoxUI ui = comboBox.getUI();
    ComboPopup popup = null;
    if (ui instanceof BasicComboBoxUI) {
      popup = ReflectionUtil.getField(BasicComboBoxUI.class, ui, ComboPopup.class, "popup");
    }

    return popup;
  }

  private class MacComboBoxTextField extends JBTextField implements DocumentListener, FocusListener {
    private MacComboBoxTextField() {
      final InputMap inputMap = getInputMap();

      inputMap.put(KeyStroke.getKeyStroke("DOWN"), "aquaSelectNext");
      inputMap.put(KeyStroke.getKeyStroke("KP_DOWN"), "aquaSelectNext");
      inputMap.put(KeyStroke.getKeyStroke("UP"), "aquaSelectPrevious");
      inputMap.put(KeyStroke.getKeyStroke("KP_UP"), "aquaSelectPrevious");

      inputMap.put(KeyStroke.getKeyStroke("HOME"), "aquaSelectHome");
      inputMap.put(KeyStroke.getKeyStroke("END"), "aquaSelectEnd");
      inputMap.put(KeyStroke.getKeyStroke("PAGE_UP"), "aquaSelectPageUp");
      inputMap.put(KeyStroke.getKeyStroke("PAGE_DOWN"), "aquaSelectPageDown");

      inputMap.put(KeyStroke.getKeyStroke("ENTER"), "aquaEnterPressed");
      inputMap.put(KeyStroke.getKeyStroke("SPACE"), "aquaSpacePressed");

      //getActionMap().put("macEnterPressed", macEnterPressedAction);
      //getDocument().addDocumentListener(this);

      addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          if ("enabled".equals(evt.getPropertyName())) {
            if (SystemInfo.isMac && UIUtil.isUnderIntelliJLaF()) {
              //ignore
            } else {
              setBorder(Boolean.TRUE.equals(evt.getNewValue()) ? EDITOR_BORDER : DISABLED_EDITOR_BORDER);
            }

            repaint();
          }
        }
      });

      addFocusListener(this);
    }

    @Override
    public void updateUI() {
      super.updateUI();
      
      if (SystemInfo.isMac && UIUtil.isUnderIntelliJLaF()) {
        setBorder(JBUI.Borders.empty());
        setOpaque(false);
      } else {
        setBorder(isEnabled() ? EDITOR_BORDER : DISABLED_EDITOR_BORDER);
      }
    }

    @Override
    public boolean hasFocus() {
      final Container parent = getParent();
      if (parent instanceof ComboBox && ((ComboBox)parent).myPaintingNow) {
        return false; // to disable focus painting around combobox button
      }
      return super.hasFocus();
    }

    @Override
    public void focusGained(FocusEvent e) {
      repaintCombobox();
    }

    private void repaintCombobox() {
      final Container parent = getParent();
      if (parent == null) return;
      if (parent instanceof JComponent && Boolean.TRUE == ((JComponent)parent).getClientProperty("JComboBox.isTableCellEditor")) return;
      final Container grandParent = parent.getParent();
      if (grandParent != null) {
        grandParent.repaint();
      }
    }

    @Override
    public void focusLost(FocusEvent e) {
      repaintCombobox();
    }

    @Override
    public Dimension getMinimumSize() {
      final Dimension minimumSize = super.getMinimumSize();
      return new Dimension(minimumSize.width, minimumSize.height + 2);
    }

    @Override
    public Dimension getPreferredSize() {
      return getMinimumSize();
    }

    @Override
    public void setBounds(final int x, final int y, final int width, final int height) {
      UIUtil.setComboBoxEditorBounds(x, y, width, height, this);
    }

    @Override
    public Color getBackground() {
      if (SystemInfo.isMac && UIUtil.isUnderIntelliJLaF()) {
        Container parent = getParent();
        if (parent != null && !parent.isEnabled()) {
          return Gray.xF8;
        }
      }
      return super.getBackground();
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
      textChanged();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      textChanged();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
      textChanged();
    }

    private void textChanged() {
      final Container ancestor = SwingUtilities.getAncestorOfClass(JComboBox.class, this);
      if (ancestor == null || !ancestor.isVisible()) return;

      final JComboBox comboBox = (JComboBox)ancestor;
      if (!comboBox.isPopupVisible()) return;

      final ComboPopup popup = getComboboxPopup(comboBox);
      if (popup == null) return;

      String s = myField.getText();

      final ListModel listmodel = comboBox.getModel();
      int i = listmodel.getSize();
      if (s.length() > 0) {
        for (int j = 0; j < i; j++) {
          Object obj = listmodel.getElementAt(j);
          if (obj == null) continue;

          String s1 = obj.toString();
          if (s1 != null && (s1.startsWith(s) || s1.equals(s))) {
            popup.getList().setSelectedIndex(j);
            return;
          }
        }
      }

      popup.getList().clearSelection();
    }
  }

  public static class MacComboBoxEditorBorder implements Border {

    private boolean myDisabled;

    public MacComboBoxEditorBorder(final boolean disabled) {
      myDisabled = disabled;
    }

    @Override
    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      Color topColor;
      Color secondTopColor;
      Color leftRightColor;
      Color bottomColor;

      if (myDisabled) {
        topColor = Gray._200;
        secondTopColor = Gray._250;
        leftRightColor = Gray._205;
        bottomColor = Gray._220;
      }
      else {
        topColor = Gray._150;
        secondTopColor = Gray._230;
        leftRightColor = Gray._175;
        bottomColor = Gray._200;
      }

      int _y = y + MacUIUtil.MAC_COMBO_BORDER_V_OFFSET;
      
      g.setColor(topColor);
      g.drawLine(x + 3, _y + 3, x + width - 1, _y + 3);

      g.setColor(secondTopColor);
      g.drawLine(x + 3, _y + 4, x + width - 1, _y + 4);

      g.setColor(leftRightColor);
      g.drawLine(x + 3, _y + 4, x + 3, _y + height - 4);
      g.drawLine(x + width - 1, _y + 4, x + width - 1, _y + height - 4);

      g.setColor(bottomColor);
      g.drawLine(x + 4, _y + height - 4, x + width - 2, _y + height - 4);

      g.setColor(UIUtil.getPanelBackground());

      g.fillRect(x,  y, width, 3 + (SystemInfo.isMacOSLion ? 1 : 0));
      g.fillRect(x, _y, 3, height);
      g.fillRect(x, _y + height - 3, width, 3);
    }

    @Override
    public Insets getBorderInsets(final Component c) {
      return new Insets(6, 6, 4, 3);
    }

    @Override
    public boolean isBorderOpaque() {
      return true;
    }
  }
}