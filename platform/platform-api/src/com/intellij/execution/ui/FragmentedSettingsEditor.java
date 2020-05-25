// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.openapi.options.CompositeSettingsBuilder;
import com.intellij.openapi.options.CompositeSettingsEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class FragmentedSettingsEditor<Settings extends FragmentedSettings> extends CompositeSettingsEditor<Settings> {

  private final NotNullLazyValue<Collection<SettingsEditorFragment<Settings, ?>>> myFragments =
    NotNullLazyValue.createValue(() -> createFragments());

  protected abstract Collection<SettingsEditorFragment<Settings, ?>> createFragments();

  protected final List<SettingsEditorFragment<Settings, ?>> getFragments() {
    return new ArrayList<>(myFragments.getValue());
  }

  @Override
  public void resetEditorFrom(@NotNull Settings settings) {
    super.resetEditorFrom(settings);
    @Nullable Set<String> visibleFragments = settings.getSelectedOptions();
    for (SettingsEditorFragment<Settings, ?> fragment : getFragments()) {
      fragment.setSelected(visibleFragments.isEmpty() ?
                           fragment.isInitiallyVisible(settings) :
                           visibleFragments.contains(fragment.getId()));
    }
  }

  @Override
  public void applyEditorTo(@NotNull Settings settings) throws ConfigurationException {
    super.applyEditorTo(settings);
    settings.setSelectedOptions(
      getFragments().stream().filter(fragment -> fragment.isSelected()).map(fragment -> fragment.getId()).collect(Collectors.toSet()));
  }

  @Override
  public CompositeSettingsBuilder<Settings> getBuilder() {
    return new FragmentedSettingsBuilder<>(getFragments(), null);
  }
}
