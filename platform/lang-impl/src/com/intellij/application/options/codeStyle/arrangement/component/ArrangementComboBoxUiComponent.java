/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement.component;

import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 3/11/13 11:56 AM
 */
public class ArrangementComboBoxUiComponent extends AbstractArrangementUiComponent {
  
  @NotNull private final JComboBox myComboBox;

  @SuppressWarnings("unchecked")
  public ArrangementComboBoxUiComponent(@NotNull List<ArrangementSettingsToken> tokens) {
    super(tokens);
    ArrangementSettingsToken[] tokensArray = tokens.toArray(new ArrangementSettingsToken[tokens.size()]);
    Arrays.sort(tokensArray, new Comparator<ArrangementSettingsToken>() {
      @Override
      public int compare(ArrangementSettingsToken t1, ArrangementSettingsToken t2) {
        return t1.getRepresentationValue().compareTo(t2.getRepresentationValue());
      }
    });
    myComboBox = new JComboBox(tokensArray);
    myComboBox.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        setText(((ArrangementSettingsToken)value).getRepresentationValue());
      }
    });
    myComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          fireStateChanged();
        }
      }
    });
    int minWidth = 0;
    ListCellRenderer renderer = myComboBox.getRenderer();
    JBList dummyList = new JBList();
    for (int i = 0, max = myComboBox.getItemCount(); i < max; i++) {
      Component rendererComponent = renderer.getListCellRendererComponent(dummyList, myComboBox.getItemAt(i), i, false, true);
      minWidth = Math.max(minWidth, rendererComponent.getPreferredSize().width);
    }
    myComboBox.setPreferredSize(new Dimension(minWidth * 5 / 3, myComboBox.getPreferredSize().height));
  }

  @NotNull
  @Override
  public ArrangementSettingsToken getToken() {
    return (ArrangementSettingsToken)myComboBox.getSelectedItem();
  }

  @Override
  public void chooseToken(@NotNull ArrangementSettingsToken data) throws IllegalArgumentException {
    myComboBox.setSelectedItem(data); 
  }

  @NotNull
  @Override
  public ArrangementMatchCondition getMatchCondition() {
    ArrangementSettingsToken token = getToken();
    return new ArrangementAtomMatchCondition(token, token);
  }

  @Override
  protected JComponent doGetUiComponent() {
    return myComboBox;
  }

  @Override
  public boolean isSelected() {
    return true;
  }

  @Override
  public void setSelected(boolean selected) {
  }

  @Override
  public boolean isEnabled() {
    return myComboBox.isEnabled();
  }

  @Override
  public void setEnabled(boolean enabled) {
    myComboBox.setEnabled(enabled); 
  }

  @Override
  protected void doReset() {
  }

  @Override
  public int getBaselineToUse(int width, int height) {
    return -1;
  }
}
