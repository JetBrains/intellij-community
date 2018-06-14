/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ConfigurableFactory;
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel;
import com.intellij.application.options.codeStyle.CodeStyleSchemesPanel;
import com.intellij.application.options.codeStyle.CodeStyleSettingsListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
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
    super.apply();
    myModel.apply();

    for (CodeStyleConfigurableWrapper panel : myPanels) {
      panel.applyPanel();
    }

    //noinspection deprecation
    CodeStyleSettingsManager.getInstance(myProject).fireCodeStyleSettingsChanged(null);
  }

  @Override
  protected Configurable[] buildConfigurables() {
    myPanels = new ArrayList<>();

    final List<CodeStyleSettingsProvider> providers =
      Arrays.asList(Extensions.getExtensions(CodeStyleSettingsProvider.EXTENSION_POINT_NAME));
    providers.sort((p1, p2) -> {
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
        CodeStyleConfigurableWrapper e = ConfigurableFactory.Companion.getInstance().createCodeStyleConfigurable(provider, ensureModel(), this);
        myPanels.add(e);
      }
    }

    int size = myPanels.size();
    Configurable[] result = new Configurable[size > 0 ? size - 1 : 0];
    for (int i = 0; i < result.length; i++) {
      result[i] = myPanels.get(i + 1);
    }
    return result;
  }

  void resetCompleted() {
    myRevertCompleted = false;
  }

  CodeStyleSchemesModel ensureModel() {
    if (myModel == null) {
      myModel = new CodeStyleSchemesModel(myProject);
      myRootSchemesPanel = new CodeStyleSchemesPanel(myModel, 0);

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

  @Override
  public boolean isModified() {
    if (myModel != null) {
      if (myModel.containsModifiedCodeStyleSettings()) return true;
      for (Configurable panel : myPanels) {
        if (panel.isModified()) return true;
      }
      boolean schemeListModified = myModel.isSchemeListModified();
      if (schemeListModified) {
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
}
