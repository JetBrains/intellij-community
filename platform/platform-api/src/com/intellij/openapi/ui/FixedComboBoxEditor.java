// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.Gray;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.lang.reflect.Method;

/**
 * Use this editor if you wish your combobox editor to look good on Macs.
 *
 * User: spLeaner
 */
public class FixedComboBoxEditor implements ComboBoxEditor {
  @NotNull
  private final JBTextField myField = UIUtil.isUnderDefaultMacTheme() ? new MacComboBoxTextField() : new JBTextField();
  private Object oldValue;

  @NotNull
  public JBTextField getField() {
    return myField;
  }

  @Override
  public Component getEditorComponent() {
    return myField;
  }

  @Override
  public void setItem(Object anObject) {
    if (anObject != null) {
      myField.setText(anObject.toString());
      oldValue = anObject;
    }
    else {
      myField.setText("");
    }
  }

  @Override
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
          Method method = cls.getMethod("valueOf", String.class);
          newValue = method.invoke(oldValue, myField.getText());
        }
        catch (Exception ex) {
          // Fail silently and return the newValue (a String object)
        }
      }
    }
    return newValue;
  }

  @Override
  public void selectAll() {
    myField.selectAll();
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myField, true));
  }

  @Override public void addActionListener(ActionListener l) {}

  @Override public void removeActionListener(ActionListener l) {}

  @Nullable
  private static ComboPopup getComboboxPopup(JComboBox comboBox) {
    ComboBoxUI ui = comboBox.getUI();
    ComboPopup popup = null;
    if (ui instanceof BasicComboBoxUI) {
      popup = ReflectionUtil.getField(BasicComboBoxUI.class, ui, ComboPopup.class, "popup");
    }

    return popup;
  }

  private final class MacComboBoxTextField extends JBTextField implements DocumentListener, FocusListener {
    private MacComboBoxTextField() {
      InputMap inputMap = getInputMap();

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

      addFocusListener(this);
    }

    @Override
    public boolean hasFocus() {
      Container parent = getParent();
      if (parent instanceof ComboBox && ((ComboBox)parent).myPaintingNow) {
        return false; // to disable focus painting around combobox button
      }
      return super.hasFocus();
    }

    @Override
    public void focusGained(FocusEvent e) {
      repaintCombobox();
    }

    @Override
    public void focusLost(FocusEvent e) {
      repaintCombobox();
    }

    private void repaintCombobox() {
      Container parent = getParent();

      if (parent == null || parent instanceof JComponent && Boolean.TRUE == ((JComponent)parent).getClientProperty("JComboBox.isTableCellEditor")) return;

      Container grandParent = parent.getParent();
      if (grandParent != null) {
        grandParent.repaint();
      }
    }

    @Override
    public Color getBackground() {
      if (UIUtil.isUnderDefaultMacTheme()) {
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
      Container ancestor = SwingUtilities.getAncestorOfClass(JComboBox.class, this);
      if (ancestor == null || !ancestor.isVisible()) return;

      JComboBox comboBox = (JComboBox)ancestor;
      if (!comboBox.isPopupVisible()) return;

      ComboPopup popup = getComboboxPopup(comboBox);
      if (popup == null) return;

      String s = myField.getText();

      ListModel listmodel = comboBox.getModel();
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
}