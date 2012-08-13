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

import com.intellij.util.Consumer;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * Editor for choosing a single value from a set of predefined values.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/13/12 4:15 PM
 */
public class ArrangementAtomEditor extends JPanel {
  
  @NotNull private final Dimension myPrefSize;
  
  public ArrangementAtomEditor(@NotNull String[] values, @NotNull final Consumer<String> callback) {
    JComboBox box = new JComboBox(values);
    box.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          callback.consume((String)e.getItem());
        }
      }
    });
    
    setLayout(new GridBagLayout());
    add(box, new GridBag().anchor(GridBagConstraints.CENTER));

    FontMetrics metrics = box.getFontMetrics(box.getFont());
    int maxWidth = 0;
    String widestText = null;
    for (String value : values) {
      int width = metrics.stringWidth(value);
      if (width > maxWidth) {
        widestText = value;
        maxWidth = width;
      }
    }
    if (widestText != null) {
      box.setSelectedItem(widestText);
    }
    myPrefSize = getPreferredSize();
    if (values.length > 0) {
      box.setSelectedItem(values[0]);
    }
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
