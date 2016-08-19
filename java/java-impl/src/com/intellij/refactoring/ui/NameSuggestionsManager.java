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
package com.intellij.refactoring.ui;

import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author dsl
 */
public class NameSuggestionsManager {
  private final TypeSelector myTypeSelector;
  private final NameSuggestionsField myNameField;
  private final NameSuggestionsGenerator myGenerator;

  private final HashMap<PsiType, SuggestedNameInfo> myTypesToSuggestions = new HashMap<>();

  public NameSuggestionsManager(TypeSelector typeSelector, NameSuggestionsField nameField, NameSuggestionsGenerator generator) {
    myTypeSelector = typeSelector;
    myNameField = nameField;
    myGenerator = generator;

    myTypeSelector.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateSuggestions(myTypeSelector.getSelectedType());
        }
      }
    });
    updateSuggestions(myTypeSelector.getSelectedType());

  }

  public void nameSelected() {
    SuggestedNameInfo nameInfo = myTypesToSuggestions.get(myTypeSelector.getSelectedType());

    if (nameInfo != null) {
      nameInfo.nameChosen(myNameField.getEnteredName());
    }
  }

  private void updateSuggestions(PsiType selectedType) {
    SuggestedNameInfo nameInfo = myTypesToSuggestions.get(selectedType);
    if (nameInfo == null) {
      nameInfo = myGenerator.getSuggestedNameInfo(selectedType);
      myTypesToSuggestions.put(selectedType, nameInfo);
    }
    myNameField.setSuggestions(nameInfo.names);
  }

  public void setLabelsFor(JLabel typeSelectorLabel, JLabel nameLabel) {
    if(myTypeSelector.getFocusableComponent() != null) {
      typeSelectorLabel.setLabelFor(myTypeSelector.getFocusableComponent());
    }

    if(myNameField.getFocusableComponent() != null) {
      nameLabel.setLabelFor(myNameField.getFocusableComponent());
    }
  }
}
