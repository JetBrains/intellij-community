// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.application.options.editor.EditorOptionsProvider;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class CodeCompletionOptions extends CompositeConfigurable<UnnamedConfigurable> implements EditorOptionsProvider, Configurable.WithEpDependencies {
  public static final String ID = "editor.preferences.completion";

  private CodeCompletionPanel codeCompletionPanel;

  @Override
  public boolean isModified() {
    return super.isModified() || codeCompletionPanel != null && codeCompletionPanel.isModified();
  }

  @Override
  public JComponent createComponent() {
    List<UnnamedConfigurable> configurables = getConfigurables();
    List<UnnamedConfigurable> optionAddons = new ArrayList<>(configurables.size());
    List<UnnamedConfigurable> sectionAddons = new ArrayList<>(configurables.size());
    for (UnnamedConfigurable configurable : configurables) {
      if (configurable instanceof CodeCompletionOptionsCustomSection) {
        sectionAddons.add(configurable);
      }
      else {
        optionAddons.add(configurable);
      }
    }
    sectionAddons.sort(Comparator.comparing(c -> ObjectUtils.notNull(c instanceof Configurable ? ((Configurable)c).getDisplayName() : null, "")));
    codeCompletionPanel = new CodeCompletionPanel();
    return codeCompletionPanel.createPanel(optionAddons, sectionAddons);
  }

  @Override
  public String getDisplayName() {
    return ApplicationBundle.message("title.code.completion");
  }

  @Override
  public void reset() {
    super.reset();
    codeCompletionPanel.reset();
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    codeCompletionPanel.apply();
  }

  @Override
  public void disposeUIResources() {
    codeCompletionPanel = null;
    super.disposeUIResources();
  }

  @NotNull
  @Override
  protected List<UnnamedConfigurable> createConfigurables() {
    return ConfigurableWrapper.createConfigurables(CodeCompletionConfigurableEP.EP_NAME);
  }

  @Override
  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.editor.code.completion";
  }

  @Override
  @NotNull
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public Collection<BaseExtensionPointName<?>> getDependencies() {
    return Collections.singleton(CodeCompletionConfigurableEP.EP_NAME);
  }
}
