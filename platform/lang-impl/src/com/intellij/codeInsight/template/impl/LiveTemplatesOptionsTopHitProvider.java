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
package com.intellij.codeInsight.template.impl;

import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey.Malenkov
 */
public final class LiveTemplatesOptionsTopHitProvider extends OptionsTopHitProvider {
  @Override
  public String getId() {
    return "templates";
  }

  @NotNull
  @Override
  public Collection<BooleanOptionDescription> getOptions(@Nullable Project project) {
    TemplateSettings settings = TemplateSettings.getInstance();
    if (settings == null) {
      return Collections.emptyList();
    }
    Collection<BooleanOptionDescription> options = new ArrayList<>();
    for (TemplateGroup group : settings.getTemplateGroups()) {
      for (final TemplateImpl element : group.getElements()) {
        options.add(new Option(element));
      }
    }
    return Collections.unmodifiableCollection(options);
  }

  private static final class Option extends BooleanOptionDescription {
    private final TemplateImpl myElement;

    private Option(TemplateImpl element) {
      super(getOptionName(element), LiveTemplatesConfigurable.ID);
      myElement = element;
    }

    @Override
    public boolean isOptionEnabled() {
      return !myElement.isDeactivated();
    }

    @Override
    public void setOptionState(boolean enabled) {
      myElement.setDeactivated(!enabled);
    }

    private static String getOptionName(TemplateImpl element) {
      StringBuilder sb = new StringBuilder().append(element.getGroupName()).append(": ").append(element.getKey());
      String description = element.getDescription();
      if (description != null) {
        sb.append(" (").append(description).append(")");
      }
      return sb.toString();
    }
  }
}
