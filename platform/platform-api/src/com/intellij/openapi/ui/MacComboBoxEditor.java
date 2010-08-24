/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * User: spLeaner
 */
public class MacComboBoxEditor implements ComboBoxEditor {
  public static final Border EDITOR_BORDER = new MacComboBoxEditorBorder(false);
  public static final Border DISABLED_EDITOR_BORDER = new MacComboBoxEditorBorder(true);

  private MacComboBoxTextField myField;
  private Object oldValue;

  public MacComboBoxEditor() {
    myField = new MacComboBoxTextField();
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
    myField.requestFocus();
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
      try {
        final Field popupField = BasicComboBoxUI.class.getDeclaredField("popup");
        popupField.setAccessible(true);
        popup = (ComboPopup)popupField.get(ui);
      }
      catch (NoSuchFieldException e1) {
        popup = null;
      }
      catch (IllegalAccessException e1) {
        popup = null;
      }
    }

    return popup;
  }

  private class MacComboBoxTextField extends JTextField implements DocumentListener, FocusListener {
    private boolean myRepaintingParent;

    private MacComboBoxTextField() {
      setBorder(isEnabled() ? EDITOR_BORDER : DISABLED_EDITOR_BORDER);
      //setFont(UIUtil.getListFont());

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
            setBorder(Boolean.TRUE.equals(evt.getNewValue()) ? EDITOR_BORDER : DISABLED_EDITOR_BORDER);
            repaint();
          }
        }
      });

      addFocusListener(this);
    }

    @Override
    public boolean hasFocus() {
      if (myRepaintingParent) {
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
      assert parent != null;
      final Container grandParent = parent.getParent();
      if (grandParent != null) {
        myRepaintingParent = true;
        grandParent.repaint();
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            myRepaintingParent = false;
          }
        });
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
        topColor = new Color(200, 200, 200);
        secondTopColor = new Color(250, 250, 250);
        leftRightColor = new Color(205, 205, 205);
        bottomColor = new Color(220, 220, 220);
      }
      else {
        topColor = new Color(150, 150, 150);
        secondTopColor = new Color(230, 230, 230);
        leftRightColor = new Color(175, 175, 175);
        bottomColor = new Color(200, 200, 200);
      }

      g.setColor(topColor);
      g.drawLine(x + 3, y + 3, x + width - 1, y + 3);

      g.setColor(secondTopColor);
      g.drawLine(x + 3, y + 4, x + width - 1, y + 4);

      g.setColor(leftRightColor);
      g.drawLine(x + 3, y + 4, x + 3, y + height - 4);
      g.drawLine(x + width - 1, y + 4, x + width - 1, y + height - 4);

      g.setColor(bottomColor);
      g.drawLine(x + 4, y + height - 4, x + width - 2, y + height - 4);

      g.setColor(UIManager.getColor("Panel.background"));

      g.fillRect(x, y, width, 3);
      g.fillRect(x, y, 3, height);
      g.fillRect(x, y + height - 3, width, 3);
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

  private static abstract class MacComboBoxAction extends AbstractAction {

    @Override
    public void actionPerformed(final ActionEvent e) {
      final Object source = e.getSource();
      if (source instanceof JComboBox) {
        final JComboBox comboBox = (JComboBox)source;
        if (!comboBox.isEnabled() || !comboBox.isShowing()) return;

        if (comboBox.isPopupVisible()) {
          ComboPopup popup = getComboboxPopup(comboBox);
          if (popup != null) {
            performComboBoxAction(comboBox, popup);
          }
        }
        else {
          comboBox.setPopupVisible(true);
        }
      }
    }

    protected abstract void performComboBoxAction(final JComboBox comboBox, final ComboPopup popup);
  }

  private AbstractAction macEnterPressedAction = new AbstractAction() {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (!myField.isEnabled() || !myField.isShowing()) return;

      final Container ancestor = SwingUtilities.getAncestorOfClass(JComboBox.class, myField);
      if (ancestor == null) return;

      final JComboBox comboBox = (JComboBox)ancestor;
      if (!comboBox.isEnabled()) return;

      if (!comboBox.isPopupVisible()) {
        selectAll();
        return;
      }

      ComboPopup popup = getComboboxPopup(comboBox);
      if (popup != null) {
        if (popup.getList().getSelectedIndex() < 0) {
          comboBox.setPopupVisible(false);
        }

        if (Boolean.TRUE.equals(comboBox.getClientProperty("JComboBox.isTableCellEditor"))) {
          comboBox.setSelectedIndex(popup.getList().getSelectedIndex());
          return;
        }

        if (comboBox.isPopupVisible()) {
          comboBox.setSelectedIndex(popup.getList().getSelectedIndex());
          comboBox.setPopupVisible(false);

          selectAll();
        }
      }
    }
  };

  private MacComboBoxAction highlightNextAction = new MacComboBoxAction() {
    @Override
    protected void performComboBoxAction(final JComboBox comboBox, final ComboPopup popup) {
      int i = comboBox.getSelectedIndex();

      if (i < comboBox.getModel().getSize() - 1) {
        comboBox.setSelectedIndex(i + 1);
        //comboBox.ensureIndexIsVisible(i + 1);
      }

      comboBox.repaint();
    }
  };

  private MacComboBoxAction highlightPreviousAction = new MacComboBoxAction() {
    @Override
    protected void performComboBoxAction(final JComboBox comboBox, final ComboPopup popup) {
      final JList list = popup.getList();
      int i = list.getSelectedIndex();
      if (i > 0) {
        list.setSelectedIndex(i - 1);
        list.ensureIndexIsVisible(i - 1);
      }

      list.repaint();
    }
  };

  private MacComboBoxAction highlightFirstAction = new MacComboBoxAction() {
    @Override
    protected void performComboBoxAction(final JComboBox comboBox, final ComboPopup popup) {
      final JList list = popup.getList();
      list.setSelectedIndex(0);
      list.ensureIndexIsVisible(0);
    }
  };

  private MacComboBoxAction highlightLastAction = new MacComboBoxAction() {
    @Override
    protected void performComboBoxAction(JComboBox comboBox, final ComboPopup popup) {
      final JList list = popup.getList();
      int i = list.getModel().getSize();
      list.setSelectedIndex(i - 1);
      list.ensureIndexIsVisible(i - 1);
    }
  };

  MacComboBoxAction highlightPageUpAction = new MacComboBoxAction() {
    @Override
    protected void performComboBoxAction(final JComboBox comboBox, final ComboPopup popup) {
      final JList list = popup.getList();
      int i = list.getSelectedIndex();
      int j = list.getFirstVisibleIndex();

      if (i != j) {
        list.setSelectedIndex(j);
        return;
      }

      int k = list.getVisibleRect().height / list.getCellBounds(0, 0).height;
      int l = j - k;
      if (l < 0) l = 0;

      list.ensureIndexIsVisible(l);
      list.setSelectedIndex(l);
    }
  };

  private MacComboBoxAction highlightPageDownAction = new MacComboBoxAction() {
    @Override
    protected void performComboBoxAction(JComboBox comboBox, final ComboPopup popup) {
      final JList list = popup.getList();
      int i = list.getSelectedIndex();
      int j = list.getLastVisibleIndex();

      if (i != j) {
        list.setSelectedIndex(j);
        return;
      }

      int k = list.getVisibleRect().height / list.getCellBounds(0, 0).height;
      int l = list.getModel().getSize() - 1;
      int i1 = j + k;
      if (i1 > l) i1 = l;

      list.ensureIndexIsVisible(i1);
      list.setSelectedIndex(i1);
    }
  };
}