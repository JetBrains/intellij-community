// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.group;

import com.intellij.ConfigurableFactory;
import com.intellij.application.options.CodeStyleConfigurableWrapper;
import com.intellij.application.options.CodeStyleSchemesConfigurable;
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.psi.codeStyle.CodeStyleGroup;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CodeStyleGroupProvider extends CodeStyleSettingsProvider {
  private final CodeStyleGroup myGroup;
  private final CodeStyleSchemesModel myModel;
  private final CodeStyleSchemesConfigurable mySchemesConfigurable;
  private final List<CodeStyleSettingsProvider> myChildProviders = new ArrayList<>();

  public CodeStyleGroupProvider(@NotNull CodeStyleGroup group,
                                CodeStyleSchemesModel model,
                                CodeStyleSchemesConfigurable configurable) {
    myGroup = group;
    myModel = model;
    mySchemesConfigurable = configurable;
  }

  public Configurable createConfigurable() {
    return new CodeStyleGroupConfigurable();
  }

  @NotNull
  @Override
  public Configurable createSettingsPage(CodeStyleSettings settings, CodeStyleSettings modelSettings) {
    return new CodeStyleGroupConfigurable();
  }

  public void addChildProvider(@NotNull CodeStyleSettingsProvider provider) {
    myChildProviders.add(provider);
  }

  public class CodeStyleGroupConfigurable extends SearchableConfigurable.Parent.Abstract {

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
      return myGroup.getDisplayName();
    }

    @Override
    public void reset() {
      myModel.reset();
      for (Configurable child : getConfigurables()) {
        if (child instanceof CodeStyleConfigurableWrapper) {
          ((CodeStyleConfigurableWrapper)child).resetPanel();
        }
      }
    }

    @Override
    public void apply() throws ConfigurationException {
      myModel.apply();
      for (Configurable child : getConfigurables()) {
        if (child instanceof CodeStyleConfigurableWrapper) {
          ((CodeStyleConfigurableWrapper)child).applyPanel();
        }
      }
    }

    @Override
    public Configurable @NotNull [] buildConfigurables() {
      List<Configurable> childConfigurables = new ArrayList<>();
      for (CodeStyleSettingsProvider childProvider : myChildProviders) {
        CodeStyleConfigurableWrapper wrapper =
          ConfigurableFactory.Companion.getInstance().createCodeStyleConfigurable(childProvider, myModel, mySchemesConfigurable);
        childConfigurables.add(wrapper);
      }
      return childConfigurables.toArray(new Configurable[0]);
    }

    @NotNull
    @Override
    public String getId() {
      return myGroup.getId();
    }

    @Nullable
    @Override
    public String getHelpTopic() {
      return myGroup.getHelpTopic();
    }
  }
}
