/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.formatting.contextConfiguration;

import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCodeFragmentFilter;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType.SPACING_SETTINGS;
import static com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType.WRAPPING_AND_BRACES_SETTINGS;

class CodeFragmentCodeStyleSettingsPanel extends TabbedLanguageCodeStylePanel {
  private static final Logger LOG = Logger.getInstance(CodeFragmentCodeStyleSettingsPanel.class);

  private final CodeStyleSettingsCodeFragmentFilter.CodeStyleSettingsToShow mySettingsToShow;
  private final SelectedTextFormatter mySelectedTextFormatter;
  private SpacesPanelWithoutPreview mySpacesPanel;

  private Runnable mySomethingChangedCallback;

  public CodeFragmentCodeStyleSettingsPanel(@NotNull CodeStyleSettings settings,
                                            @NotNull CodeStyleSettingsCodeFragmentFilter.CodeStyleSettingsToShow settingsToShow,
                                            @NotNull Language language,
                                            @NotNull SelectedTextFormatter selectedTextFormatter)
  {
    super(language, settings, settings.clone());
    mySettingsToShow = settingsToShow;
    mySelectedTextFormatter = selectedTextFormatter;

    ensureTabs();
  }

  public void setOnSomethingChangedCallback(Runnable runnable) {
    mySomethingChangedCallback = runnable;
  }

  @Override
  protected void somethingChanged() {
    mySomethingChangedCallback.run();
  }

  @Override
  protected String getPreviewText() {
    return null;
  }

  @Override
  protected void updatePreview(boolean useDefaultSample) {
  }

  @Override
  protected void initTabs(CodeStyleSettings settings) {
    mySpacesPanel = new SpacesPanelWithoutPreview(settings);
    addTab(mySpacesPanel);
    addTab(new WrappingAndBracesPanelWithoutPreview(settings));
    reset(getSettings());
  }

  public JComponent getPreferredFocusedComponent() {
    return mySpacesPanel.getPreferredFocusedComponent();
  }

  public static CodeStyleSettingsCodeFragmentFilter.CodeStyleSettingsToShow calcSettingNamesToShow(CodeStyleSettingsCodeFragmentFilter filter) {
    return filter.getFieldNamesAffectingCodeFragment(SPACING_SETTINGS, WRAPPING_AND_BRACES_SETTINGS);
  }

  public static boolean hasOptionsToShow(LanguageCodeStyleSettingsProvider provider) {
    LanguageCodeStyleSettingsProvider.SettingsType[] types = { SPACING_SETTINGS, WRAPPING_AND_BRACES_SETTINGS };
    for (LanguageCodeStyleSettingsProvider.SettingsType type : types) {
      if (!provider.getSupportedFields(type).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private void reformatSelectedTextWithNewSettings() {
    try {
      apply(getSettings());
    }
    catch (ConfigurationException e) {
      LOG.debug("Cannot apply code style settings", e);
    }

    CodeStyleSettings clonedSettings = getSettings().clone();
    mySelectedTextFormatter.reformatSelectedText(clonedSettings);
  }

  private class SpacesPanelWithoutPreview extends MySpacesPanel {
    private JPanel myPanel;

    public SpacesPanelWithoutPreview(CodeStyleSettings settings) {
      super(settings);
    }

    @Override
    protected void somethingChanged() {
      mySelectedTextFormatter.restoreSelectedText();
      reformatSelectedTextWithNewSettings();
      CodeFragmentCodeStyleSettingsPanel.this.somethingChanged();
    }

    @Override
    protected void init() {
      List<String> settingNames = mySettingsToShow.getSettings(getSettingsType());
      String[] names = ContainerUtil.toArray(settingNames, new String[settingNames.size()]);
      showStandardOptions(names);
      initTables();

      myOptionsTree = createOptionsTree();
      myOptionsTree.setCellRenderer(new MyTreeCellRenderer());

      JBScrollPane pane = new JBScrollPane(myOptionsTree) {
        @Override
        public Dimension getMinimumSize() {
          return super.getPreferredSize();
        }
      };

      myPanel = new JPanel(new BorderLayout());
      myPanel.add(pane);

      isFirstUpdate = false;
    }
    
    @Override
    public JComponent getPanel() {
      return myPanel;
    }

    @Override
    protected String getPreviewText() {
      return null;
    }

    public JComponent getPreferredFocusedComponent() {
      return myOptionsTree;
    }
  }

  private class WrappingAndBracesPanelWithoutPreview extends MyWrappingAndBracesPanel {
    public JPanel myPanel;

    public WrappingAndBracesPanelWithoutPreview(CodeStyleSettings settings) {
      super(settings);
    }

    @Override
    protected void init() {
      Collection<String> settingNames = mySettingsToShow.getSettings(getSettingsType());
      initTables();

      Collection<String> fields = populateWithAssociatedFields(settingNames);
      fields.add("KEEP_LINE_BREAKS");

      String[] names = ContainerUtil.toArray(fields, new String[fields.size()]);
      showStandardOptions(names);

      myTreeTable = createOptionsTree(getSettings());
      JBScrollPane scrollPane = new JBScrollPane(myTreeTable) {
        @Override
        public Dimension getMinimumSize() {
          return super.getPreferredSize();
        }
      };

      myPanel = new JPanel(new BorderLayout());
      myPanel.add(scrollPane);

      showStandardOptions(names);

      isFirstUpdate = false;
    }

    @NotNull
    private Collection<String> populateWithAssociatedFields(Collection<String> settingNames) {
      Set<String> commonFields = ContainerUtil.newHashSet();
      for (String fieldName : settingNames) {
        SettingsGroup settingsGroup = getAssociatedSettingsGroup(fieldName);
        if (settingsGroup == null) {
          commonFields.add(fieldName);
        }
        else if (settingsGroup.title != WRAPPING_KEEP) {
          commonFields.addAll(settingsGroup.commonCodeStyleSettingFieldNames);
        }
      }
      return commonFields;
    }

    @Override
    public JComponent getPanel() {
      return myPanel;
    }
    
    @Override
    protected void somethingChanged() {
      mySelectedTextFormatter.restoreSelectedText();
      reformatSelectedTextWithNewSettings();
      CodeFragmentCodeStyleSettingsPanel.this.somethingChanged();
    }
    
    @Override
    protected String getPreviewText() {
      return null;
    }
  }
}
