/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.application.options;

import com.intellij.application.options.codeStyle.*;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class CodeStyleSchemesConfigurable extends SearchableConfigurable.Parent.Abstract
  implements OptionsContainingConfigurable, Configurable.NoMargin, Configurable.NoScroll, Configurable.VariableProjectAppLevel {

  private CodeStyleSchemesPanel myRootSchemesPanel;
  private CodeStyleSchemesModel myModel;
  private List<CodeStyleConfigurableWrapper> myPanels;
  private boolean myResetCompleted = false;
  private boolean myInitResetInvoked = false;
  private boolean myRevertCompleted = false;

  private boolean myApplyCompleted = false;
  private final Project myProject;

  public CodeStyleSchemesConfigurable(Project project) {
    myProject = project;
  }

  @Override
  public JComponent createComponent() {
    myModel = ensureModel();

    return myPanels == null || myPanels.isEmpty() ? null : myPanels.get(0).createComponent();
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  public void disposeUIResources() {
    if (myPanels != null) {
      try {
        super.disposeUIResources();
        for (CodeStyleConfigurableWrapper panel : myPanels) {
          panel.disposeUIResources();
        }
      }
      finally {
        myPanels = null;
        myModel = null;
        myRootSchemesPanel = null;
        myResetCompleted = false;
        myRevertCompleted = false;
        myApplyCompleted = false;
        myInitResetInvoked = false;
      }
    }
  }

  @Override
  public synchronized void reset() {
    if (!myInitResetInvoked) {
      try {
        if (!myResetCompleted) {
          try {
            resetImpl();
          }
          finally {
            myResetCompleted = true;
          }
        }
      }
      finally {
        myInitResetInvoked = true;
      }
    }
    else {
      revert();
    }

  }

  private void resetImpl() {
    if (myModel != null) {
      myModel.reset();
    }

    if (myPanels != null) {
      for (CodeStyleConfigurableWrapper panel : myPanels) {
        panel.resetPanel();
      }
    }
  }

  public synchronized void resetFromChild() {
    if (!myResetCompleted) {
      try {
        resetImpl();
      }
      finally {
        myResetCompleted = true;
      }
    }
  }

  public void revert() {
    if (myModel.isSchemeListModified() || isSomeSchemeModified()) {
      myRevertCompleted = false;
    }
    if (!myRevertCompleted) {
      try {
        resetImpl();
      }
      finally {
        myRevertCompleted = true;
      }
    }
  }

  private boolean isSomeSchemeModified() {
    if (myPanels != null) {
      for (CodeStyleConfigurableWrapper panel : myPanels) {
        if (panel.isPanelModified()) return true;
      }
    }

    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (!myApplyCompleted) {
      try {
        super.apply();

        for (CodeStyleScheme scheme : new ArrayList<>(myModel.getSchemes())) {
          final boolean isDefaultModified = CodeStyleSchemesModel.cannotBeModified(scheme) && isSchemeModified(scheme);
          if (isDefaultModified) {
            CodeStyleScheme newscheme = myModel.createNewScheme(null, scheme);
            CodeStyleSettings settingsWillBeModified = scheme.getCodeStyleSettings();
            CodeStyleSettings notModifiedSettings = settingsWillBeModified.clone();
            ((CodeStyleSchemeImpl)scheme).setCodeStyleSettings(notModifiedSettings);
            ((CodeStyleSchemeImpl)newscheme).setCodeStyleSettings(settingsWillBeModified);
            myModel.addScheme(newscheme, false);

            if (myModel.getSelectedScheme() == scheme) {
              myModel.selectScheme(newscheme, this);
            }

          }
        }

        for (CodeStyleConfigurableWrapper panel : myPanels) {
          panel.applyPanel();
        }

        myModel.apply();
        EditorFactory.getInstance().refreshAllEditors();
      }
      finally {
        myApplyCompleted = true;
      }

    }

  }

  private boolean isSchemeModified(final CodeStyleScheme scheme) {
    for (CodeStyleConfigurableWrapper panel : myPanels) {
      if (panel.isPanelModified(scheme)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected Configurable[] buildConfigurables() {
    myPanels = new ArrayList<>();

    final List<CodeStyleSettingsProvider> providers =
      Arrays.asList(Extensions.getExtensions(CodeStyleSettingsProvider.EXTENSION_POINT_NAME));
    Collections.sort(providers, (p1, p2) -> {
      if (!p1.getPriority().equals(p2.getPriority())) {
        return p1.getPriority().compareTo(p2.getPriority());
      }
      String name1 = p1.getConfigurableDisplayName();
      if (name1 == null) name1 = "";
      String name2 = p2.getConfigurableDisplayName();
      if (name2 == null) name2 = "";
      return name1.compareToIgnoreCase(name2);
    });

    for (final CodeStyleSettingsProvider provider : providers) {
      if (provider.hasSettingsPage()) {
        myPanels.add(new CodeStyleConfigurableWrapper(provider, new CodeStyleSettingsPanelFactory() {
          @Override
          public NewCodeStyleSettingsPanel createPanel(final CodeStyleScheme scheme) {
            return new NewCodeStyleSettingsPanel(provider.createSettingsPage(scheme.getCodeStyleSettings(), ensureModel().getCloneSettings(scheme)));
          }
        }));
      }
    }

    int size = myPanels.size();
    Configurable[] result = new Configurable[size > 0 ? size - 1 : 0];
    for (int i = 0; i < result.length; i++) {
      result[i] = myPanels.get(i + 1);
    }
    return result;
  }

  private CodeStyleSchemesModel ensureModel() {
    if (myModel == null) {
      myModel = new CodeStyleSchemesModel(myProject);
      myRootSchemesPanel = new CodeStyleSchemesPanel(myModel);

      myModel.addListener(new CodeStyleSettingsListener(){
        @Override
        public void currentSchemeChanged(final Object source) {
          if (source != myRootSchemesPanel) {
            myRootSchemesPanel.onSelectedSchemeChanged();
          }
        }

        @Override
        public void schemeListChanged() {
          myRootSchemesPanel.resetSchemesCombo();
        }

        @Override
        public void currentSettingsChanged() {

        }

        @Override
        public void usePerProjectSettingsOptionChanged() {
          myRootSchemesPanel.usePerProjectSettingsOptionChanged();
        }

        @Override
        public void schemeChanged(final CodeStyleScheme scheme) {
          if (scheme == myModel.getSelectedScheme()) myRootSchemesPanel.onSelectedSchemeChanged();
        }
      });
    }
    return myModel;
  }

  @Override
  public String getDisplayName() {
    return "Code Style";
  }

  @Override
  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.globalcodestyle";
  }

  public void selectPage(Class pageToSelect) {
    //TODO lesya
    //getActivePanel().selectTab(pageToSelect);
  }

  @Override
  public boolean isModified() {
    if (myModel != null) {
      if (myPanels != null && myPanels.size() > 0 && myPanels.get(0).isModified()) {
        return true;
      }
      boolean schemeListModified = myModel.isSchemeListModified();
      if (schemeListModified) {
        myApplyCompleted = false;
        myRevertCompleted = false;
      }
      return schemeListModified;
    }

    return false;
  }

  @Override
  @NotNull
  public String getId() {
    return "preferences.sourceCode";
  }

  @Override
  public Set<String> processListOptions() {
    HashSet<String> result = new HashSet<>();
    for (CodeStyleConfigurableWrapper panel : myPanels) {
      result.addAll(panel.processListOptions());
    }
    return result;
  }

  @Override
  public boolean isProjectLevel() {
    return myModel != null && myModel.isUsePerProjectSettings();
  }

  @Nullable
  public SearchableConfigurable findSubConfigurable(final String name) {
    if (myPanels == null) {
      buildConfigurables();
    }
    return myPanels.stream().filter(panel -> panel.getDisplayName().equals(name)).findFirst().orElse(null);
  }

  private class CodeStyleConfigurableWrapper implements SearchableConfigurable, NoMargin, NoScroll, OptionsContainingConfigurable {
    private boolean myInitialResetInvoked;
    private CodeStyleMainPanel myPanel;
    private final CodeStyleSettingsProvider myProvider;
    private final CodeStyleSettingsPanelFactory myFactory;

    public CodeStyleConfigurableWrapper(@NotNull CodeStyleSettingsProvider provider, @NotNull CodeStyleSettingsPanelFactory factory) {
      myProvider = provider;
      myFactory = factory;
      myInitialResetInvoked = false;
    }

    @Override
    @Nls
    public String getDisplayName() {
      String displayName = myProvider.getConfigurableDisplayName();
      if (displayName != null) return displayName;
      
      return myPanel != null ? myPanel.getDisplayName() : null;  // fallback for 8.0 API compatibility
    }

    @Override
    public String getHelpTopic() {
      return myPanel != null ? myPanel.getHelpTopic() : null;
    }

    @Override
    public JComponent createComponent() {
      if (myPanel == null) {
        myPanel = new CodeStyleMainPanel(ensureModel(), myFactory);
      }
      return myPanel;
    }

    @Override
    public boolean isModified() {
      if (myPanel != null) {
        boolean someSchemeModified = myPanel.isModified();
        if (someSchemeModified) {
          myApplyCompleted = false;
          myRevertCompleted = false;
        }
        return someSchemeModified;
      }
      return false;
    }

    @Override
    public void apply() throws ConfigurationException {
      CodeStyleSchemesConfigurable.this.apply();
    }

    public void resetPanel() {
      if (myPanel != null) {
        myPanel.reset();
      }
    }

    @Override
    public String toString() {
      return myProvider.getClass().getName();
    }

    @Override
    public void reset() {
      if (!myInitialResetInvoked) {
        try {
          resetFromChild();
        }
        finally {
          myInitialResetInvoked = true;
        }
      }
      else {
        revert();
      }
    }

    @Override
    @NotNull
    public String getId() {
      return "preferences.sourceCode." + getDisplayName();
    }

    @Override
    public void disposeUIResources() {
      if (myPanel != null) {
        myPanel.disposeUIResources();
      }
    }

    public boolean isPanelModified(CodeStyleScheme scheme) {
      return myPanel != null && myPanel.isModified(scheme);
    }

    public boolean isPanelModified() {
      return myPanel != null && myPanel.isModified();
    }

    public void applyPanel() throws ConfigurationException {
      if (myPanel != null) {
        myPanel.apply();
      }
    }

    @Override
    public Set<String> processListOptions() {
      if (myPanel == null) {
        myPanel = new CodeStyleMainPanel(ensureModel(), myFactory);
      }
      return myPanel.processListOptions();
    }
  }
}
