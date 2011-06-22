/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.ui;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.DialogUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class ComboBoxVisibilityPanel extends VisibilityPanelBase /*implements ShortcutProvider*/ {
  private final JLabel myLabel;
  private final JComboBox myComboBox;
  private final Map<String, String> myNamesMap = new HashMap<String, String>();

  public ComboBoxVisibilityPanel(String name, String[] options, String[] presentableNames) {
    setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 2, true, false));
    myLabel = new JLabel(name);
    add(myLabel);
    myComboBox = new JComboBox(presentableNames);
    add(myComboBox);
    for (int i = 0; i < options.length; i++) {
      myNamesMap.put(options[i], presentableNames[i]);
    }
    myComboBox.addActionListener(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myEventDispatcher.getMulticaster().stateChanged(new ChangeEvent(ComboBoxVisibilityPanel.this));
      }
    });
    DialogUtil.registerMnemonic(myLabel, myComboBox);
  }

  public ComboBoxVisibilityPanel(String name, String[] options) {
    this(name, options, options);
  }

  public ComboBoxVisibilityPanel(String[] options) {
    this(RefactoringBundle.message("visibility.combo.title"), options);
  }

  public ComboBoxVisibilityPanel(String[] options, String[] presentableNames) {
    this(RefactoringBundle.message("visibility.combo.title"), options, presentableNames);
  }

  public void setDisplayedMnemonicIndex(int index) {
    myLabel.setDisplayedMnemonicIndex(index);
  }

  @Override
  public String getVisibility() {
    final String selected = (String)myComboBox.getSelectedItem();
    return ContainerUtil.reverseMap(myNamesMap).get((selected));
  }

  public void addListener(ChangeListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @Override
  public void setVisibility(String visibility) {
    myComboBox.setSelectedItem(myNamesMap.get(visibility));
    myEventDispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
  }
}
