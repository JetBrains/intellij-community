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
package com.intellij.application.options.codeStyle.arrangement.editor;

import com.intellij.application.options.codeStyle.arrangement.ArrangementNodeDisplayManager;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Editor for choosing a single value from a set of predefined values.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/13/12 4:15 PM
 */
public class ArrangementAtomEditor extends JPanel {

  @NotNull private final Map<Object, Object> myValues = new HashMap<Object, Object>();

  @NotNull private final Dimension myPrefSize;
  @NotNull private final JComboBox myValueBox;

  public ArrangementAtomEditor(@NotNull Collection<?> values, @NotNull ArrangementNodeDisplayManager manager) {
    String[] uiValues = new String[values.size()];
    int i = 0;
    for (Object value : values) {
      String uiValue = manager.getDisplayValue(value);
      uiValues[i++] = uiValue;
      myValues.put(uiValue, value);
    }
    Arrays.sort(uiValues);
    myValueBox = new JComboBox(uiValues);
    setLayout(new GridBagLayout());
    add(myValueBox, new GridBag().anchor(GridBagConstraints.CENTER).weightx(1).fillCellHorizontally());

    FontMetrics metrics = myValueBox.getFontMetrics(myValueBox.getFont());
    int maxWidth = 0;
    String widestText = null;
    for (String value : uiValues) {
      int width = metrics.stringWidth(value);
      if (width > maxWidth) {
        widestText = value;
        maxWidth = width;
      }
    }
    if (widestText != null) {
      myValueBox.setSelectedItem(widestText);
    }
    myPrefSize = super.getPreferredSize();
    if (uiValues.length > 0) {
      myValueBox.setSelectedItem(uiValues[0]);
    }
  }

  @NotNull
  public Object getValue() {
    return myValues.get(myValueBox.getSelectedItem());
  }

  public void applyColorsFrom(@NotNull JComponent component) {
    myValueBox.setBackground(component.getBackground());
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getPreferredSize() {
    return myPrefSize;
  }
}
