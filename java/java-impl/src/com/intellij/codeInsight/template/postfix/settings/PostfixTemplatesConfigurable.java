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
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.application.options.editor.EditorOptionsProvider;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

public class PostfixTemplatesConfigurable implements SearchableConfigurable, EditorOptionsProvider, Configurable.NoScroll {
  @Nullable
  private PostfixTemplatesListPanel myTemplatesListPanel;
  @NotNull
  private final PostfixTemplatesSettings myTemplatesSettings;

  private JComponent myPanel;
  private JBCheckBox myCompletionEnabledCheckbox;
  private JBCheckBox myPostfixTemplatesEnabled;
  private JPanel myTemplatesListPanelContainer;
  private ComboBox myShortcutComboBox;

  private static final String SPACE = CodeInsightBundle.message("template.shortcut.space");
  private static final String TAB = CodeInsightBundle.message("template.shortcut.tab");
  private static final String ENTER = CodeInsightBundle.message("template.shortcut.enter");

  @SuppressWarnings("unchecked")
  public PostfixTemplatesConfigurable() {
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    if (settings == null) {
      throw new RuntimeException("Can't retrieve postfix template settings");
    }

    myTemplatesSettings = settings;
    myTemplatesListPanel = new PostfixTemplatesListPanel(Arrays.asList(PostfixTemplate.EP_NAME.getExtensions()));
    myTemplatesListPanelContainer.setLayout(new BorderLayout());
    myTemplatesListPanelContainer.add(myTemplatesListPanel.getComponent(), BorderLayout.CENTER);
    myPostfixTemplatesEnabled.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateComponents();
      }
    });
    myShortcutComboBox.addItem(TAB);
    myShortcutComboBox.addItem(SPACE);
    myShortcutComboBox.addItem(ENTER);
  }

  @NotNull
  @Override
  public String getId() {
    return "reference.settingsdialog.IDE.editor.postfix.templates";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Postfix Templates";
  }

  @Nullable
  public PostfixTemplatesListPanel getTemplatesListPanel() {
    return myTemplatesListPanel;
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myTemplatesListPanel != null) {
      Map<String, Boolean> newTemplatesState = ContainerUtil.newHashMap();
      for (Map.Entry<String, Boolean> entry : myTemplatesListPanel.getState().entrySet()) {
        Boolean value = entry.getValue();
        if (value != null && !value) {
          newTemplatesState.put(entry.getKey(), entry.getValue());
        }
      }
      myTemplatesSettings.setTemplatesState(newTemplatesState);
      myTemplatesSettings.setPostfixTemplatesEnabled(myPostfixTemplatesEnabled.isSelected());
      myTemplatesSettings.setTemplatesCompletionEnabled(myCompletionEnabledCheckbox.isSelected());
      myTemplatesSettings.setShortcut(stringToShortcut((String)myShortcutComboBox.getSelectedItem()));
    }
  }

  @Override
  public void reset() {
    if (myTemplatesListPanel != null) {
      myTemplatesListPanel.setState(myTemplatesSettings.getTemplatesState());
      myPostfixTemplatesEnabled.setSelected(myTemplatesSettings.isPostfixTemplatesEnabled());
      myCompletionEnabledCheckbox.setSelected(myTemplatesSettings.isTemplatesCompletionEnabled());
      myShortcutComboBox.setSelectedItem(shortcutToString((char)myTemplatesSettings.getShortcut()));
      updateComponents();
    }
  }

  @Override
  public boolean isModified() {
    if (myTemplatesListPanel == null) {
      return false;
    }
    return myPostfixTemplatesEnabled.isSelected() != myTemplatesSettings.isPostfixTemplatesEnabled() ||
           myCompletionEnabledCheckbox.isSelected() != myTemplatesSettings.isTemplatesCompletionEnabled() ||
           stringToShortcut((String)myShortcutComboBox.getSelectedItem()) != myTemplatesSettings.getShortcut() ||
           !myTemplatesListPanel.getState().equals(myTemplatesSettings.getTemplatesState());
  }

  @Override
  public void disposeUIResources() {
    myTemplatesListPanel = null;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String s) {
    return null;
  }

  private void updateComponents() {
    boolean pluginEnabled = myPostfixTemplatesEnabled.isSelected();
    myCompletionEnabledCheckbox.setEnabled(pluginEnabled);
    myShortcutComboBox.setEnabled(pluginEnabled);
    if (myTemplatesListPanel != null) {
      myTemplatesListPanel.setEnabled(pluginEnabled);
    }
  }

  private static char stringToShortcut(@NotNull String string) {
    if (SPACE.equals(string)) {
      return TemplateSettings.SPACE_CHAR;
    }
    else if (ENTER.equals(string)) {
      return TemplateSettings.ENTER_CHAR;
    }
    return TemplateSettings.TAB_CHAR;
  }

  private static String shortcutToString(char shortcut) {
    if (shortcut == TemplateSettings.SPACE_CHAR) {
      return SPACE;
    } 
    if (shortcut == TemplateSettings.ENTER_CHAR) {
      return ENTER;
    }
    return TAB;
  }
}