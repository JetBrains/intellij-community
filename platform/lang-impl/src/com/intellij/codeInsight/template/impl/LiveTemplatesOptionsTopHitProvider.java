// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

final class LiveTemplatesOptionsTopHitProvider implements OptionsTopHitProvider.ApplicationLevelProvider {
  @Override
  public @NotNull String getId() {
    return "templates";
  }

  @Override
  public @NotNull Collection<OptionDescription> getOptions() {
    TemplateSettings settings = TemplateSettings.getInstance();
    if (settings == null) {
      return Collections.emptyList();
    }
    Collection<OptionDescription> options = new ArrayList<>();
    for (TemplateGroup group : settings.getTemplateGroups()) {
      for (final TemplateImpl element : group.getElements()) {
        options.add(new Option(element));
      }
    }
    return options;
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

    private static @NlsContexts.Label String getOptionName(TemplateImpl element) {
      @NlsContexts.Label StringBuilder sb = new StringBuilder().append(element.getGroupName()).append(": ").append(element.getKey());
      String description = element.getDescription();
      if (description != null) {
        sb.append(" (").append(description).append(")");
      }
      return sb.toString();
    }
  }
}
