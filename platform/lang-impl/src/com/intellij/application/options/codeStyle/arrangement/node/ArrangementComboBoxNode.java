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
package com.intellij.application.options.codeStyle.arrangement.node;

import com.intellij.application.options.codeStyle.arrangement.ArrangementNodeDisplayManager;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 9/27/12 5:01 PM
 */
public class ArrangementComboBoxNode<T> /*extends ArrangementTreeNode implements ArrangementRepresentationAwareNode, ArrangementEditableNode*/ {

  @NotNull private final JPanel    myControl  = new JPanel(new GridBagLayout());
  @NotNull private final JLabel    myLabel    = new JLabel();
  @NotNull private final JComboBox myComboBox = new JComboBox();
  @NotNull private final List<T>   myData     = new ArrayList<T>();

  public ArrangementComboBoxNode(@NotNull ArrangementNodeDisplayManager displayManager, @NotNull String text, @NotNull Iterable<T> data) {
    //super(null);
    for (T t : data) {
      myData.add(t);
      myComboBox.addItem(displayManager.getDisplayValue(t));
    }
    myLabel.setText(text);
    myControl.add(myLabel, new GridBag().setColumn(0).insets(0, 3, 0, 0));
    myControl.add(myComboBox, new GridBag().setColumn(1));
  }

  @NotNull
  //@Override
  public JComponent getEditor() {
    return myControl;
  }

  @NotNull
  //@Override
  public JComponent getRenderer() {
    return myControl;
  }

  public void setEnabled(boolean enabled) {
    myLabel.setEnabled(enabled);
    myComboBox.setEnabled(enabled);
  }

  @NotNull
  public T getSelectedValue() {
    int index = myComboBox.getSelectedIndex();
    if (index < 0 || index >= myData.size()) {
      return myData.get(0);
    }
    return myData.get(index);
  }

  public void setSelectedValue(@NotNull T value) {
    int i = myData.indexOf(value);
    if (i >= 0) {
      myComboBox.setSelectedIndex(i);
    }
  }

  @Override
  public String toString() {
    return myData.toString();
  }
}
