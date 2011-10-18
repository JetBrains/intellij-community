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

import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.psi.codeStyle.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Contains additional settings used in TabbedLanguageCodeStylePanel.
 * @author Rustam Vishnyakov
 */
public class AdditionalCodeStylePanel {
  private JPanel myContentPane;
  private JButton myLanguageButton;
  private TabbedLanguageCodeStylePanel myParent;
  private PredefinedCodeStyle[] myPredefinedCodeStyles;
  private CodeStyleSettings mySettings;
  private PopupMenu myLangMenu;

  public AdditionalCodeStylePanel(TabbedLanguageCodeStylePanel parent, CodeStyleSettings settings) {
    myParent = parent;
    mySettings = settings;
    myPredefinedCodeStyles = getPredefinedStyles();

    myLanguageButton.setPreferredSize(new Dimension(20, 20));
    myLanguageButton.setMnemonic(KeyEvent.VK_C);
    myLanguageButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showMenu();
      }
    });
  }

  private void showMenu() {
    if (myLangMenu == null) {
      myLangMenu = new PopupMenu();
      if (myPredefinedCodeStyles.length > 0) {
        Menu langs = new Menu("Language"); //TODO<rv>: Move to resource bundle
        myLangMenu.add(langs);
        fillLanguages(langs);
        Menu predefined = new Menu("Predefined Style"); //TODO<rv>: Move to resource bundle
        myLangMenu.add(predefined);
        fillPredefined(predefined);
      }
      else {
        fillLanguages(myLangMenu);
      }
      myLanguageButton.add(myLangMenu);
    }
    myLangMenu.show(myLanguageButton, 0, 0);
  }

  public JPanel getContentPane() {
    return myContentPane;
  }
  
  private void fillLanguages(Menu parentMenu) {
    Language[] languages = LanguageCodeStyleSettingsProvider.getLanguagesWithCodeStyleSettings();
    @SuppressWarnings("UnnecessaryFullyQualifiedName")
    java.util.List<MenuItem> langItems = new ArrayList<MenuItem>();
    for (final Language lang : languages) {
      if (!lang.equals(myParent.getDefaultLanguage())) {
        final String langName = LanguageCodeStyleSettingsProvider.getLanguageName(lang);
        MenuItem langItem = new MenuItem(langName);
        langItem.addActionListener(new ActionListener(){
          @Override
          public void actionPerformed(ActionEvent e) {
            applyLanguageSettings(lang);
          }
        });
        langItems.add(langItem);
      }
    }
    Collections.sort(langItems, new Comparator<MenuItem>(){
      @Override
      public int compare(MenuItem item1, MenuItem item2) {
        return item1.getLabel().compareToIgnoreCase(item2.getLabel());
      }
    });
    for (MenuItem langItem : langItems) {
      parentMenu.add(langItem);
    }
  }

  private void fillPredefined(Menu parentMenu) {
    for (final PredefinedCodeStyle predefinedCodeStyle : myPredefinedCodeStyles) {
      MenuItem predefinedItem = new MenuItem(predefinedCodeStyle.getName());
      parentMenu.add(predefinedItem);
      predefinedItem.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          applyPredefinedStyle(predefinedCodeStyle.getName());
        }
      });
    }
  }
  
  private PredefinedCodeStyle[] getPredefinedStyles() {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(myParent.getDefaultLanguage());
    if (provider == null) return new PredefinedCodeStyle[0];
    return provider.getPredefinedCodeStyles();
  } 


  private void applyLanguageSettings(Language lang) {
    final Project currProject = ProjectUtil.guessCurrentProject(myParent.getPanel());
    CodeStyleSettings rootSettings = CodeStyleSettingsManager.getSettings(currProject);
    CommonCodeStyleSettings sourceSettings = rootSettings.getCommonSettings(lang);
    CommonCodeStyleSettings targetSettings = mySettings.getCommonSettings(myParent.getDefaultLanguage());
    if (sourceSettings == null || targetSettings == null) return;
    CommonCodeStyleSettingsManager.copy(sourceSettings, targetSettings);
    myParent.reset(mySettings);
    myParent.onSomethingChanged();
  }
  
  private void applyPredefinedStyle(String styleName) {
    for (PredefinedCodeStyle style : myPredefinedCodeStyles) {
      if (style.getName().equals(styleName)) {
        myParent.applyPredefinedSettings(style);
      }
    }
  }

}
