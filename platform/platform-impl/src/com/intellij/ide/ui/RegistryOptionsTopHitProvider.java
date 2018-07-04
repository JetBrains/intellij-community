// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ExperimentalFeature;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class RegistryOptionsTopHitProvider extends OptionsTopHitProvider {
  @NotNull
  @Override
  public Collection<OptionDescription> getOptions(@Nullable Project project) {
    return Holder.ourValues;
  }

  @Override
  public boolean isEnabled(@Nullable Project project) {
    return ApplicationManager.getApplication().isInternal();
  }

  @Override
  public String getId() {
    return "registry";
  }

  private static class Holder {
    private static final List<OptionDescription> ourValues = initValues();

    private static List<OptionDescription> initValues() {
      final List<OptionDescription> result = new ArrayList<>();
      for (RegistryValue value : Registry.getAll()) {
        if (value.isBoolean()) {
          final String key = value.getKey();
          RegistryBooleanOptionDescriptor optionDescriptor = new RegistryBooleanOptionDescriptor(key, key);
          if (value.isChangedFromDefault()) {
            result.add(0, optionDescriptor);
          } else {
            result.add(optionDescriptor);
          }
        } else {
          result.add(new RegistryTextOptionDescriptor(value));
        }
      }
      for (ExperimentalFeature feature : Experiments.EP_NAME.getExtensions()) {
        ExperimentalFeatureBooleanOptionDescriptor descriptor = new ExperimentalFeatureBooleanOptionDescriptor(feature.id, feature.id);
        if (Experiments.isChanged(feature.id)) {
          result.add(0, descriptor);
        } else {
          result.add(descriptor);
        }
      }
      return result;
    }
  }
}
