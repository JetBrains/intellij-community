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
package com.intellij.application.options.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.PredefinedCodeStyle;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class PredefinedCodeStylesDialog extends DialogWrapper {

  private JPanel contentPane;
  private JBList myPredefinedCodeStyleList;
  private LanguageCodeStyleSettingsProvider myProvider;

  protected PredefinedCodeStylesDialog(Component parent, Language language) {
    super(parent, false);
    myProvider = LanguageCodeStyleSettingsProvider.forLanguage(language);
    MyListModel model = new MyListModel();
    myPredefinedCodeStyleList.setModel(model);
    setTitle("Select Predefined Style"); //TODO<rv> Move to resource bundle
    init();
  }
  
  @Nullable
  public PredefinedCodeStyle getSelectedStyle() {
    Object selection = myPredefinedCodeStyleList.getSelectedValue();
    return selection instanceof PredefinedCodeStyle ? (PredefinedCodeStyle)selection : null;
  }


  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }
  
  private class MyListModel extends AbstractListModel {
    private PredefinedCodeStyle[] myCodeStyles;
    
    public MyListModel() {
      myCodeStyles = myProvider.getPredefinedCodeStyles();
    }
    

    @Override
    public int getSize() {
      return myCodeStyles.length;
    }

    @Override
    public Object getElementAt(int index) {
      return myCodeStyles[index];
    }
  }
}
