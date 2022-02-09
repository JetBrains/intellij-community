// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.application.options.RegistryManager;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ExperimentalFeature;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.RegistryManagerImpl;
import com.intellij.openapi.util.registry.RegistryValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
final class RegistryOptionsTopHitProvider implements OptionsTopHitProvider.ApplicationLevelProvider {
  RegistryOptionsTopHitProvider() {
    if (!ApplicationManager.getApplication().isInternal()) {
      throw ExtensionNotApplicableException.create();
    }
  }

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions() {
    List<OptionDescription> result = new ArrayList<>();
    for (RegistryValue value : ((RegistryManagerImpl)RegistryManager.getInstance()).getAll()) {
      if (value.isBoolean()) {
        String key = value.getKey();
        RegistryBooleanOptionDescriptor optionDescriptor = new RegistryBooleanOptionDescriptor(key, key);
        if (value.isChangedFromDefault()) {
          result.add(0, optionDescriptor);
        }
        else {
          result.add(optionDescriptor);
        }
      }
      else {
        result.add(new RegistryTextOptionDescriptor(value));
      }
    }
    List<ExperimentalFeature> experimentalFeatureList = Experiments.EP_NAME.getExtensionList();
    if (!experimentalFeatureList.isEmpty()) {
      Experiments experiments = Experiments.getInstance();
      for (ExperimentalFeature feature : experimentalFeatureList) {
        @NlsSafe String optionName = feature.id; // Probably need to add localized feature.name
        ExperimentalFeatureBooleanOptionDescriptor descriptor = new ExperimentalFeatureBooleanOptionDescriptor(optionName, feature.id);
        if (experiments.isChanged(feature.id)) {
          result.add(0, descriptor);
        }
        else {
          result.add(descriptor);
        }
      }
    }
    return result;
  }

  @NotNull
  @Override
  public String getId() {
    return "registry";
  }
}
