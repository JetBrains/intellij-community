/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class CodeStyleSchemesConfigurable extends SearchableConfigurable.Parent.Abstract {

  private CodeStyleSchemesPanel myRootSchemesPanel;
  private CodeStyleSchemesModel myModel;
  private List<CodeStyleConfigurableWrapper> myPanels;
  private boolean myResetCompleted = false;
  private boolean myInitResetInvoked = false;
  private boolean myRevertCompleted = false;

  private boolean myApplyCompleted = false;
  private final Project myProject;
  private final LanguageSelector myLangSelector;

  public CodeStyleSchemesConfigurable(Project project) {
    myProject = project;
    myLangSelector = new LanguageSelector();
  }

  public JComponent createComponent() {
    myModel = ensureModel();

    return myRootSchemesPanel.getPanel();
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

        for (CodeStyleScheme scheme : new ArrayList<CodeStyleScheme>(myModel.getSchemes())) {
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

  protected Configurable[] buildConfigurables() {
    myPanels = new ArrayList<CodeStyleConfigurableWrapper>();

    for (final CodeStyleSettingsProvider provider : Extensions.getExtensions(CodeStyleSettingsProvider.EXTENSION_POINT_NAME)) {
      if (provider.hasSettingsPage()) {
        myPanels.add(new CodeStyleConfigurableWrapper(provider, new CodeStyleSettingsPanelFactory() {
          public NewCodeStyleSettingsPanel createPanel(final CodeStyleScheme scheme) {
            return new NewCodeStyleSettingsPanel(provider.createSettingsPage(scheme.getCodeStyleSettings(), ensureModel().getCloneSettings(scheme)));
          }
        }));
      }
    }

    return myPanels.toArray(new Configurable[myPanels.size()]);
  }

  private CodeStyleSchemesModel ensureModel() {
    if (myModel == null) {
      myModel = new CodeStyleSchemesModel(myProject);
      myRootSchemesPanel = new CodeStyleSchemesPanel(myModel);

      myModel.addListener(new CodeStyleSettingsListener(){
        public void currentSchemeChanged(final Object source) {
          if (source != myRootSchemesPanel) {
            myRootSchemesPanel.onSelectedSchemeChanged();
          }
        }

        public void schemeListChanged() {
          myRootSchemesPanel.resetSchemesCombo();
        }

        public void currentSettingsChanged() {
          
        }

        public void usePerProjectSettingsOptionChanged() {
          myRootSchemesPanel.usePerProjectSettingsOptionChanged();
        }

        public void schemeChanged(final CodeStyleScheme scheme) {
          //do nothing
        }
      });
    }
    return myModel;
  }

  public String getDisplayName() {
    return "Code Style";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableCodeStyle.png");
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.globalcodestyle";
  }

  public void selectPage(Class pageToSelect) {
    //TODO lesya
    //getActivePanel().selectTab(pageToSelect);
  }

  public boolean isModified() {
    if (myModel != null) {
      boolean schemeListModified = myModel.isSchemeListModified();
      if (schemeListModified) {
        myApplyCompleted = false;
        myRevertCompleted = false;
      }
      return schemeListModified;
    }

    return false;
  }

  public String getId() {
    return "preferences.sourceCode";
  }

  public HashSet<OptionDescription> processOptions() {
    return new HashSet<OptionDescription>();
    //TODO lesya
    //return getActivePanel().processOptions();
  }

  private class CodeStyleConfigurableWrapper implements SearchableConfigurable, NoScroll {
    private boolean myInitialResetInvoked;
    private CodeStyleMainPanel myPanel;
    private final CodeStyleSettingsProvider myProvider;
    private final CodeStyleSettingsPanelFactory myFactory;

    public CodeStyleConfigurableWrapper(CodeStyleSettingsProvider provider, CodeStyleSettingsPanelFactory factory) {
      myProvider = provider;
      myFactory = factory;
      myInitialResetInvoked = false;
    }

    @Nls
    public String getDisplayName() {
      String displayName = myProvider.getConfigurableDisplayName();
      if (displayName != null) return displayName;
      
      return ensurePanel().getDisplayName();  // fallback for 8.0 API compatibility
    }

    public Icon getIcon() {
      return null;
    }

    public String getHelpTopic() {
      return ensurePanel().getHelpTopic();
    }

    private CodeStyleMainPanel ensurePanel() {
      if (myPanel == null) {
        myPanel = new CodeStyleMainPanel(ensureModel(), myLangSelector, myFactory);
      }
      return myPanel;
    }

    public JComponent createComponent() {
      return ensurePanel();
    }

    public boolean isModified() {
      boolean someSchemeModified = ensurePanel().isModified();
      if (someSchemeModified) {
        myApplyCompleted = false;
        myRevertCompleted = false;
      }
      return someSchemeModified;
    }

    public void apply() throws ConfigurationException {
      CodeStyleSchemesConfigurable.this.apply();
    }

    public void resetPanel() {
      if (myPanel != null) {
        myPanel.reset();
      }
    }

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

    public String getId() {
      return "preferences.sourceCode." + getDisplayName();
    }

    public Runnable enableSearch(final String option) {
      return null;
    }

    public void disposeUIResources() {
      if (myPanel != null) {
        myPanel.disposeUIResources();
      }
    }

    public boolean isPanelModified(CodeStyleScheme scheme) {
      return ensurePanel().isModified(scheme);
    }

    public boolean isPanelModified() {
      return ensurePanel().isModified();
    }

    public void applyPanel() {
      ensurePanel().apply();
    }
  }
}