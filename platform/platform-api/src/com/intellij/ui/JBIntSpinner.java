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
package com.intellij.ui;

import com.intellij.ide.ui.UINumericRange;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class JBIntSpinner extends JSpinner {
  public JBIntSpinner(UINumericRange range) {
    this(range.initial, range.min, range.max);
  }

  public JBIntSpinner(int value, int minValue, int maxValue) {
    setModel(new SpinnerNumberModel(value, minValue, maxValue, 1));
    final NumberEditor editor = new NumberEditor(this, "#");
    JFormattedTextField textField = editor.getTextField();
    textField.setColumns(Math.max(4, textField.getColumns()));

    if (UIUtil.isUnderWin10LookAndFeel()) {
      textField.setHorizontalAlignment(SwingConstants.LEFT);
    }

    setEditor(editor);
    final MyListener listener = new MyListener();
    addMouseWheelListener(listener);
    textField.addFocusListener(listener);
    textField.addMouseListener(listener);
    addMouseListener(listener);
  }

  @Override
  public void setEditor(JComponent editor) {
    if (!(editor instanceof NumberEditor)) throw new IllegalArgumentException("JBSpinner allows NumberEditor only");
    super.setEditor(editor);
  }

  @NotNull
  private JTextField getTextField() {
    return ((NumberEditor)getEditor()).getTextField();
  }

  private SpinnerNumberModel getNumberModel() {
    return (SpinnerNumberModel)super.getModel();
  }

  public void setMin(int value) {
    getNumberModel().setMinimum(value);
  }

  public void setMax(int value) {
    getNumberModel().setMaximum(value);
  }

  public int getMin() {
    return ((Number)getNumberModel().getMinimum()).intValue();
  }

  public int getMax() {
    return ((Number)getNumberModel().getMaximum()).intValue();
  }

  public void setNumber(int value) {
    setValue(Math.max(getMin(), Math.min(getMax(), value)));
  }

  public int getNumber() {
    return getNumberModel().getNumber().intValue();
  }

  private class MyListener extends MouseAdapter implements FocusListener {
    private boolean mySelect = true;

    @Override
    public void mousePressed(MouseEvent e) {
      mySelect = false;
      Component component = e.getComponent();
      if (component == JBIntSpinner.this) {
        JTextField textField = getTextField();
        if (textField.isEnabled() ) {
          MouseEvent event = SwingUtilities.convertMouseEvent(component, e, textField);
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
            IdeFocusManager.getGlobalInstance().requestFocus(textField, true)
          );
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> textField.dispatchEvent(event));
        }
      }
    }

    @Override
    public void focusGained(FocusEvent e) {
      if (!mySelect) {
        mySelect = true;
        return;
      }
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> getTextField().selectAll());
    }


    @Override
    public void focusLost(FocusEvent e) {
      mySelect = true;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      if (e.getUnitsToScroll() == 0) return;
      JTextField field = getTextField();
      final SpinnerNumberModel model = getNumberModel();
      if (field.hasFocus() && isEnabled()) {
        model.setValue(calculateNewValue(model, e.getUnitsToScroll()));
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Number calculateNewValue(SpinnerNumberModel model, int steps) {
    final int newValue = ((Number)model.getValue()).intValue() + model.getStepSize().intValue() * steps;
    Comparable minimum = model.getMinimum();
    Comparable maximum = model.getMaximum();
    if (minimum instanceof Number && minimum.compareTo(newValue) > 0) return (Number)minimum;
    if (maximum instanceof Number && maximum.compareTo(newValue) < 0) return (Number)maximum;
    return newValue;
  }
}
