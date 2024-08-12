// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.group;

import com.intellij.ConfigurableFactory;
import com.intellij.application.options.CodeStyleConfigurableWrapper;
import com.intellij.application.options.CodeStyleSchemesConfigurable;
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel;
import com.intellij.lang.Language;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.codeStyle.CodeStyleGroup;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class CodeStyleGroupProvider extends CodeStyleSettingsProvider {
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

  @Override
  public @Nullable String getConfigurableDisplayName() {
    return myGroup.getDisplayName();
  }

  @Override
  public @Nullable Language getLanguage() {
    return myGroup.getLanguage();
  }

  @Override
  public @NotNull Configurable createSettingsPage(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings modelSettings) {
    return new CodeStyleGroupConfigurable();
  }

  public void addChildProvider(@NotNull CodeStyleSettingsProvider provider) {
    myChildProviders.add(provider);
  }

  public final class CodeStyleGroupConfigurable extends SearchableConfigurable.Parent.Abstract
    implements ConfigurableGroup, Configurable.NoScroll {

    @Override
    public @NonNls @NotNull String getId() {
      return myGroup.getId();
    }

    @Override
    public @NonNls String getHelpTopic() {
      return myGroup.getHelpTopic();
    }

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
      return myGroup.getDisplayName();
    }

    @Override
    public @NlsContexts.DetailedDescription String getDescription() {
      return myGroup.getDescription();
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
  }
}
