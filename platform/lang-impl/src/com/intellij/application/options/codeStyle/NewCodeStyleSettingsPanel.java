// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.OptionsContainingConfigurable;
import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class NewCodeStyleSettingsPanel extends JPanel implements TabbedLanguageCodeStylePanel.TabChangeListener {
  private static final OptionsContainingConfigurable EMPTY_OPTIONS_CONTAINING_CONFIGURABLE = new OptionsContainingConfigurable() {};

  private static final Logger LOG = Logger.getInstance(NewCodeStyleSettingsPanel.class);

  private final Configurable myTab;
  private final CodeStyleSchemesModel myModel;

  public NewCodeStyleSettingsPanel(@NotNull Configurable tab, @NotNull CodeStyleSchemesModel model) {
    super(new BorderLayout());

    myTab = tab;
    myModel = model;
    JComponent component = myTab.createComponent();
    if (component != null) {
      add(component, BorderLayout.CENTER);
    }
    else {
      LOG.warn("No component for " + tab.getDisplayName());
    }
  }

  public boolean isModified() {
    return myTab.isModified();
  }

  public void updatePreview() {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).getPanel().onSomethingChanged();
    }
  }

  public void apply() throws ConfigurationException {
    if (myTab.isModified()) {
      myTab.apply();
    }
  }

  public @Nullable String getHelpTopic() {
    return myTab.getHelpTopic();
  }

  public void dispose() {
    if (isValid()) {
      Container parent = getParent();
      if (parent != null && parent.isValid()) {
        parent.remove(this);
      }
    }
    myTab.disposeUIResources();
  }

  public void reset(CodeStyleSettings settings) {
    try {
      myModel.setUiEventsEnabled(false);
      if (myTab instanceof CodeStyleConfigurable) {
        ((CodeStyleConfigurable)myTab).reset(settings);
      }
      else {
        myTab.reset();
      }
      updatePreview();
    }
    finally {
      myModel.setUiEventsEnabled(true);
    }
  }

  public void reset() {
    myTab.reset();
    updatePreview();
  }

  public String getDisplayName() {
    return myTab.getDisplayName();
  }

  public void setModel(@NotNull CodeStyleSchemesModel model) {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).setModel(model);
    }
  }

  public void onSomethingChanged() {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).onSomethingChanged();
    }
  }

  public @NotNull OptionsContainingConfigurable getOptionIndexer() {
    return myTab instanceof OptionsContainingConfigurable ? (OptionsContainingConfigurable)myTab : EMPTY_OPTIONS_CONTAINING_CONFIGURABLE;
  }

  public @Nullable CodeStyleAbstractPanel getSelectedPanel() {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      return ((CodeStyleAbstractConfigurable)myTab).getPanel();
    }
    return null;
  }

  @Override
  public void tabChanged(@NotNull TabbedLanguageCodeStylePanel source, @NotNull String tabTitle) {
    CodeStyleAbstractPanel panel = getSelectedPanel();
    if (panel instanceof TabbedLanguageCodeStylePanel && panel != source) {
      ((TabbedLanguageCodeStylePanel)panel).changeTab(tabTitle);
    }
  }

  void highlightOptions(@NotNull String searchString) {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).highlightOptions(searchString);
    }
  }
}
