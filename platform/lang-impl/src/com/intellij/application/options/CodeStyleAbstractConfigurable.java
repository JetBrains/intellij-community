// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.codeStyle.CodeStyleConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;
import java.util.Set;

public abstract class CodeStyleAbstractConfigurable implements CodeStyleConfigurable, OptionsContainingConfigurable {
  private CodeStyleAbstractPanel myPanel;
  private final @NotNull CodeStyleSettings mySettings;
  private final CodeStyleSettings myCloneSettings;
  private final @NlsContexts.ConfigurableName String myDisplayName;

  public CodeStyleAbstractConfigurable(@NotNull CodeStyleSettings settings,
                                       @NotNull CodeStyleSettings cloneSettings,
                                       @NlsContexts.ConfigurableName String displayName) {
    mySettings = settings;
    myCloneSettings = cloneSettings;
    myDisplayName = displayName;
  }

  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public JComponent createComponent() {
    myPanel = createPanel(myCloneSettings);
    return myPanel.getPanel();
  }

  protected abstract @NotNull CodeStyleAbstractPanel createPanel(@NotNull CodeStyleSettings settings);

  @Override
  public void apply() throws ConfigurationException {
    apply(mySettings);
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    if (myPanel != null) {
      try {
        myPanel.apply(settings);
      }
      catch (ConfigurationException ce) {
        ce.setOriginator(this);
        throw ce;
      }
    }
  }

  @Override
  public void reset() {
    reset(mySettings);
  }

  @Override
  public void reset(@NotNull CodeStyleSettings settings) {
    if (myPanel != null) {
      myPanel.reset(settings);
    }
  }

  @Override
  public boolean isModified() {
    return myPanel != null && myPanel.isModified(mySettings);
  }

  @Override
  public void disposeUIResources() {
    if (myPanel != null) {
      Disposer.dispose(myPanel);
      myPanel = null;
    }
  }

  public CodeStyleAbstractPanel getPanel() {
    return myPanel;
  }

  public void setModel(@NotNull CodeStyleSchemesModel model) {
    if (myPanel != null) {
      myPanel.setModel(model);
    }
  }

  public void onSomethingChanged() {
    myPanel.onSomethingChanged();
  }

  @Override
  public @NotNull Set<String> processListOptions() {
    return myPanel.getOptionIndexer().processListOptions();
  }

  @Override
  public @NotNull Map<String, Set<String>> processListOptionsWithPaths() {
    return myPanel.getOptionIndexer().processListOptionsWithPaths();
  }

  protected CodeStyleSettings getCurrentSettings() {
    return mySettings;
  }

  public void highlightOptions(@NotNull String searchString) {
    myPanel.highlightOptions(searchString);
  }
}
