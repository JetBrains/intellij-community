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
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 8/13/12 12:33 PM
 */
public class ArrangementNodeEditor extends JPanel {

  @NotNull private final CardLayout myCardLayout = new CardLayout();
  @NotNull private final JPanel     myValuePanel = new JPanel(myCardLayout);

  @NotNull private final ArrangementNodeDisplayManager        myDisplayManager;
  @NotNull private final Map<ArrangementSettingType, List<?>> myAvailableSettings;
  @NotNull private final JComboBox                            myTypeComboBox;
  @NotNull private final Dimension                            myPrefSize;

  public ArrangementNodeEditor(@NotNull ArrangementNodeDisplayManager manager,
                               @NotNull Map<ArrangementSettingType, List<?>> settings)
  {
    myDisplayManager = manager;
    myAvailableSettings = settings;

    setOpaque(false);
    myTypeComboBox = new JComboBox();
    ArrangementSettingType[] types = myAvailableSettings.keySet().toArray(new ArrangementSettingType[myAvailableSettings.size()]);
    Arrays.sort(types, new Comparator<ArrangementSettingType>() {
      @Override
      public int compare(ArrangementSettingType o1, ArrangementSettingType o2) {
        return myDisplayManager.getDisplayValue(o1).compareTo(myDisplayManager.getDisplayValue(o2));
      }
    });

    for (ArrangementSettingType type : types) {
      myTypeComboBox.addItem(myDisplayManager.getDisplayValue(type));
    }

    setLayout(new GridBagLayout());
    add(myTypeComboBox, new GridBag());
    
    // TODO den remove
    myPrefSize = getPreferredSize();
  }
  

  public void applyColorsFrom(@NotNull JComponent component) {
    myTypeComboBox.setBackground(component.getBackground());
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
