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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsAtomNode;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.Consumer;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 8/13/12 12:33 PM
 */
public class ArrangementNodeEditor extends JPanel {

  @NotNull private final CardLayout                         myCardLayout = new CardLayout();
  @NotNull private final JPanel                             myValuePanel = new JPanel(myCardLayout);
  @NotNull private final Map<String, ArrangementAtomEditor> myEditors    = new HashMap<String, ArrangementAtomEditor>();

  @NotNull private final ArrangementNodeDisplayManager myDisplayManager;
  @NotNull private final JComboBox                     myTypeComboBox;
  @NotNull private final JCheckBox                     myNegateCheckBox;
  @NotNull private final Dimension                     myPrefSize;
  @NotNull private       String                        myCurrentCard;

  public ArrangementNodeEditor(@NotNull ArrangementNodeDisplayManager manager,
                               @NotNull Map<ArrangementSettingType, List<?>> settings,
                               @NotNull final Consumer<ArrangementSettingsAtomNode> resultProcessor)
  {
    myDisplayManager = manager;

    setOpaque(false);
    setBorder(IdeBorderFactory.createEmptyBorder(0, 8, 0, 8));
    setLayout(new GridBagLayout());

    myTypeComboBox = new JComboBox();
    ArrangementSettingType[] types = settings.keySet().toArray(new ArrangementSettingType[settings.size()]);
    Arrays.sort(types, new Comparator<ArrangementSettingType>() {
      @Override
      public int compare(ArrangementSettingType o1, ArrangementSettingType o2) {
        return myDisplayManager.getDisplayValue(o1).compareTo(myDisplayManager.getDisplayValue(o2));
      }
    });

    final Map<Object, ArrangementSettingType> uiText2Type = new HashMap<Object, ArrangementSettingType>();
    for (ArrangementSettingType type : types) {
      String displayValue = myDisplayManager.getDisplayValue(type);
      uiText2Type.put(displayValue, type);
      myTypeComboBox.addItem(displayValue);
    }
    myTypeComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          myCardLayout.show(myValuePanel, myCurrentCard = e.getItem().toString());
        }
      }
    });
    add(myTypeComboBox, new GridBag().insets(0, 3, 0, 0));
    
    int maxWidth = 0;
    String widestCardName = null;
    for (Map.Entry<ArrangementSettingType, List<?>> entry : settings.entrySet()) {
      List<String> values = new ArrayList<String>();
      for (Object o : entry.getValue()) {
        values.add(myDisplayManager.getDisplayValue(o));
        Collections.sort(values);
      }
      ArrangementAtomEditor atomEditor = new ArrangementAtomEditor(values, manager);
      String cardName = myDisplayManager.getDisplayValue(entry.getKey());
      myEditors.put(cardName, atomEditor);
      myValuePanel.add(atomEditor, cardName);
      Dimension size = atomEditor.getPreferredSize();
      if (maxWidth < size.width) {
        widestCardName = cardName;
        maxWidth = size.width;
      }
    }
    add(myValuePanel, new GridBag().insets(0, 8, 0, 0).fillCellHorizontally().weightx(1).coverLine());
    
    myNegateCheckBox = new JCheckBox(ApplicationBundle.message("arrangement.text.negate"));
    add(myNegateCheckBox, new GridBag().insets(0, 0, 0, 0).anchor(GridBagConstraints.WEST).coverLine());
    
    JButton okButton = new JButton(AllIcons.Actions.Checked);
    okButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ArrangementSettingType type = uiText2Type.get(myTypeComboBox.getSelectedItem());
        Object value = myEditors.get(myCurrentCard).getValue();
        resultProcessor.consume(new ArrangementSettingsAtomNode(type, value));
      }
    });
    add(okButton, new GridBag().insets(0, 3, 0, 0).weightx(1).fillCellHorizontally().coverLine());
    
    if (widestCardName != null) {
      myCardLayout.show(myValuePanel, widestCardName);
      myPrefSize = super.getPreferredSize();
    }
    else {
      myPrefSize = super.getPreferredSize();
    }
    
    myCardLayout.show(myValuePanel, myCurrentCard = myTypeComboBox.getSelectedItem().toString());
  }
  
  public void applyColorsFrom(@NotNull JComponent component) {
    myTypeComboBox.setBackground(component.getBackground());
    myNegateCheckBox.setBackground(component.getBackground());
    for (ArrangementAtomEditor editor : myEditors.values()) {
      editor.applyColorsFrom(component);
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
