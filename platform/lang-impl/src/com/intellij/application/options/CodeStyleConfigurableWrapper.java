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

import com.intellij.application.options.codeStyle.CodeStyleMainPanel;
import com.intellij.application.options.codeStyle.CodeStyleSettingsPanelFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

public class CodeStyleConfigurableWrapper
  implements SearchableConfigurable, Configurable.NoMargin, Configurable.NoScroll, OptionsContainingConfigurable {
  private boolean myInitialResetInvoked;
  protected CodeStyleMainPanel myPanel;
  private final CodeStyleSettingsProvider myProvider;
  private final CodeStyleSettingsPanelFactory myFactory;
  private final CodeStyleSchemesConfigurable myOwner;

  public CodeStyleConfigurableWrapper(@NotNull CodeStyleSettingsProvider provider, @NotNull CodeStyleSettingsPanelFactory factory, CodeStyleSchemesConfigurable owner) {
    myProvider = provider;
    myFactory = factory;
    myOwner = owner;
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
      myPanel = new CodeStyleMainPanel(myOwner.ensureModel(), myFactory, canBeShared());
    }
    return myPanel;
  }

  protected boolean canBeShared() {
    return true;
  }

  @Override
  public boolean isModified() {
    if (myPanel != null) {
      boolean someSchemeModified = myPanel.isModified();
      if (someSchemeModified) {
        myOwner.resetCompleted();
      }
      return someSchemeModified;
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    myOwner.apply();
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
        myOwner.resetFromChild();
      }
      finally {
        myInitialResetInvoked = true;
      }
    }
    else {
      myOwner.revert();
    }
  }

  @Override
  @NotNull
  public String getId() {
    return getConfigurableId(getDisplayName());
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
      myPanel = new CodeStyleMainPanel(myOwner.ensureModel(), myFactory, canBeShared());
    }
    return myPanel.processListOptions();
  }

  @NotNull
  public static String getConfigurableId(String configurableDisplayName) {
    return "preferences.sourceCode." + configurableDisplayName;
  }
}
