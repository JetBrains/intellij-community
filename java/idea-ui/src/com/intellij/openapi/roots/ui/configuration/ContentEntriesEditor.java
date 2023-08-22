// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 */
public class ContentEntriesEditor extends JavaContentEntriesEditor {
  private LanguageLevelConfigurable myLanguageLevelConfigurable;

  public ContentEntriesEditor(String moduleName, final ModuleConfigurationState state) {
    super(moduleName, state);
  }

  @Override
  public void disposeUIResources() {
    if (myLanguageLevelConfigurable != null) myLanguageLevelConfigurable.disposeUIResources();
    super.disposeUIResources();
  }

  @Override
  public boolean isModified() {
    return super.isModified() || myLanguageLevelConfigurable != null && myLanguageLevelConfigurable.isModified();
  }

  private ModifiableRootModel getModifiableModel() {
    return getModel();
  }

  @Override
  protected void addAdditionalSettingsToPanel(final JPanel mainPanel) {
    myLanguageLevelConfigurable = new LanguageLevelConfigurable(myProject, this::fireModuleConfigurationChanged) {
      @Override
      public @NotNull LanguageLevelModuleExtension getLanguageLevelExtension() {
        return getModifiableModel().getModuleExtension(LanguageLevelModuleExtension.class);
      }
    };
    mainPanel.add(myLanguageLevelConfigurable.createComponent(), BorderLayout.NORTH);
    myLanguageLevelConfigurable.reset();
  }

  private void fireModuleConfigurationChanged() {
    super.fireConfigurationChanged();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myLanguageLevelConfigurable != null) myLanguageLevelConfigurable.apply();
    super.apply();
  }
}
