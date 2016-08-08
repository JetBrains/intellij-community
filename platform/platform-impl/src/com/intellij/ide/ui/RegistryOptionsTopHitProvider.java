/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.application.ApplicationManager;
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
  public Collection<BooleanOptionDescription> getOptions(@Nullable Project project) {
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
    private static final List<BooleanOptionDescription> ourValues = initValues();

    private static List<BooleanOptionDescription> initValues() {
      final List<BooleanOptionDescription> result = new ArrayList<>();
      for (RegistryValue value : Registry.getAll()) {
        if (value.isBoolean()) {
          final String key = value.getKey();
          RegistryBooleanOptionDescriptor optionDescriptor = new RegistryBooleanOptionDescriptor(key, key);
          if (value.isChangedFromDefault()) {
            result.add(0, optionDescriptor);
          } else {
            result.add(optionDescriptor);
          }
        }
      }
      return result;
    }
  }
}
