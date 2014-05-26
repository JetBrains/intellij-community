/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

public class PostfixTemplatesConfigurable implements SearchableConfigurable, EditorOptionsProvider, Configurable.NoScroll {

  public static final Comparator<PostfixTemplate> TEMPLATE_COMPARATOR = new Comparator<PostfixTemplate>() {
    @Override
    public int compare(PostfixTemplate o1, PostfixTemplate o2) {
      return o1.getKey().compareTo(o2.getKey());
    }
  };

  @Nullable
  private PostfixTemplatesCheckboxTree myCheckboxTree;

  @NotNull
  private final PostfixTemplatesSettings myTemplatesSettings;
  @Nullable
  private PostfixDescriptionPanel myInnerPostfixDescriptionPanel;

  private MultiMap<String, PostfixTemplate> templateMultiMap;

  private JComponent myPanel;
  private JBCheckBox myCompletionEnabledCheckbox;
  private JBCheckBox myPostfixTemplatesEnabled;
  private JPanel myTemplatesTreeContainer;
  private ComboBox myShortcutComboBox;
  private JPanel myDescriptionPanel;

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

    LanguageExtensionPoint[] extensions = new ExtensionPointName<LanguageExtensionPoint>(LanguagePostfixTemplate.EP_NAME).getExtensions();


    templateMultiMap = MultiMap.create();
    for (LanguageExtensionPoint extension : extensions) {
      List<PostfixTemplate> postfixTemplates =
        ContainerUtil.newArrayList(((PostfixTemplateProvider)extension.getInstance()).getTemplates());
      if (postfixTemplates.isEmpty()) {
        continue;
      }

      ContainerUtil.sort(postfixTemplates, TEMPLATE_COMPARATOR);

      templateMultiMap.putValues(extension.getKey(), postfixTemplates);
    }

    myPostfixTemplatesEnabled.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateComponents();
      }
    });
    myShortcutComboBox.addItem(TAB);
    myShortcutComboBox.addItem(SPACE);
    myShortcutComboBox.addItem(ENTER);

    myDescriptionPanel.setLayout(new BorderLayout());
  }

  private void createTree() {
    myCheckboxTree = new PostfixTemplatesCheckboxTree() {
      @Override
      protected void selectionChanged() {
        resetDescriptionPanel();
      }
    };
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(ScrollPaneFactory.createScrollPane(myCheckboxTree));

    myTemplatesTreeContainer.setLayout(new BorderLayout());
    myTemplatesTreeContainer.add(panel);
  }

  private void resetDescriptionPanel() {
    if (null != myCheckboxTree && null != myInnerPostfixDescriptionPanel) {
      myInnerPostfixDescriptionPanel.reset(PostfixTemplateMetaData.createMetaData(myCheckboxTree.getTemplate()));
    }
  }

  @NotNull
  @Override
  public String getId() {
    return "reference.settingsdialog.IDE.editor.postfix.templates";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return getId();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Postfix Completion";
  }

  @Nullable
  public PostfixTemplatesCheckboxTree getTemplatesTree() {
    return myCheckboxTree;
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel);
    if (null == myInnerPostfixDescriptionPanel) {
      myInnerPostfixDescriptionPanel = new PostfixDescriptionPanel();
      myDescriptionPanel.add(myInnerPostfixDescriptionPanel.getComponent());
    }
    if (null == myCheckboxTree) {
      createTree();
      myCheckboxTree.initTree(templateMultiMap);
    }

    return myPanel;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myCheckboxTree != null) {
      myTemplatesSettings.setLangDisabledTemplates(myCheckboxTree.getState());
      myTemplatesSettings.setPostfixTemplatesEnabled(myPostfixTemplatesEnabled.isSelected());
      myTemplatesSettings.setTemplatesCompletionEnabled(myCompletionEnabledCheckbox.isSelected());
      myTemplatesSettings.setShortcut(stringToShortcut((String)myShortcutComboBox.getSelectedItem()));
    }
  }

  @Override
  public void reset() {
    if (myCheckboxTree != null) {
      myCheckboxTree.setState(myTemplatesSettings.getLangDisabledTemplates());
      myPostfixTemplatesEnabled.setSelected(myTemplatesSettings.isPostfixTemplatesEnabled());
      myCompletionEnabledCheckbox.setSelected(myTemplatesSettings.isTemplatesCompletionEnabled());
      myShortcutComboBox.setSelectedItem(shortcutToString((char)myTemplatesSettings.getShortcut()));
      resetDescriptionPanel();
      updateComponents();
    }
  }

  @Override
  public boolean isModified() {
    if (myCheckboxTree == null) {
      return false;
    }
    return myPostfixTemplatesEnabled.isSelected() != myTemplatesSettings.isPostfixTemplatesEnabled() ||
           myCompletionEnabledCheckbox.isSelected() != myTemplatesSettings.isTemplatesCompletionEnabled() ||
           stringToShortcut((String)myShortcutComboBox.getSelectedItem()) != myTemplatesSettings.getShortcut()
           || !myCheckboxTree.getState().equals(myTemplatesSettings.getLangDisabledTemplates());
  }

  @Override
  public void disposeUIResources() {
    if (myInnerPostfixDescriptionPanel != null) {
      Disposer.dispose(myInnerPostfixDescriptionPanel);
    }
    myCheckboxTree = null;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String s) {
    return null;
  }

  private void updateComponents() {
    boolean pluginEnabled = myPostfixTemplatesEnabled.isSelected();
    myCompletionEnabledCheckbox.setVisible(!LiveTemplateCompletionContributor.shouldShowAllTemplates());
    myCompletionEnabledCheckbox.setEnabled(pluginEnabled);
    myShortcutComboBox.setEnabled(pluginEnabled);
    if (myCheckboxTree != null) {
      myCheckboxTree.setEnabled(pluginEnabled);
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
