// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.openapi.options.CompositeSettingsBuilder;
import com.intellij.openapi.options.CompositeSettingsEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class FragmentedSettingsEditor<Settings extends FragmentedSettings> extends CompositeSettingsEditor<Settings> {

  private final NotNullLazyValue<Collection<SettingsEditorFragment<Settings, ?>>> myFragments =
    NotNullLazyValue.createValue(() -> {
      Collection<SettingsEditorFragment<Settings, ?>> fragments = createFragments();
      initFragments(fragments);
      return fragments;
    });

  protected final Settings mySettings;

  protected FragmentedSettingsEditor(Settings settings) {
    mySettings = settings;
  }

  protected boolean isDefaultSettings() {
    return false;
  }

  protected abstract Collection<SettingsEditorFragment<Settings, ?>> createFragments();

  protected final Collection<SettingsEditorFragment<Settings, ?>> getFragments() {
    return myFragments.getValue();
  }

  private Stream<SettingsEditorFragment<Settings, ?>> getAllFragments() {
    return getFragments().stream().flatMap(fragment -> Stream.concat(fragment.getChildren().stream(), Stream.of(fragment)));
  }

  @Override
  public void resetEditorFrom(@NotNull Settings settings) {
    super.resetEditorFrom(settings);
    List<FragmentedSettings.Option> options = settings.getSelectedOptions();
    for (SettingsEditorFragment<Settings, ?> fragment : getAllFragments().collect(Collectors.toList())) {
      FragmentedSettings.Option option = ContainerUtil.find(options, o -> fragment.getId().equals(o.getName()));
      fragment.setSelected(option == null ? fragment.isInitiallyVisible(settings) : option.getVisible());
    }
  }

  @Override
  public void applyEditorTo(@NotNull Settings settings) throws ConfigurationException {
    super.applyEditorTo(settings);
    List<FragmentedSettings.Option> options = getAllFragments().filter(fragment -> (isDefaultSettings() || fragment.isCanBeHidden()) &&
                                                                                   fragment.isSelected() != fragment.isInitiallyVisible(settings))
      .map(fragment -> new FragmentedSettings.Option(fragment.getId(), fragment.isSelected())).collect(Collectors.toList());
    if (!isDefaultSettings()) {
      for (FragmentedSettings.Option option : settings.getSelectedOptions()) {
        if (!ContainerUtil.or(options, o -> o.getName().equals(option.getName()))) {
          options.add(option);
        }
      }
    }
    settings.setSelectedOptions(options);
  }

  @Override
  public @NotNull CompositeSettingsBuilder<Settings> getBuilder() {
    return new FragmentedSettingsBuilder<>(getFragments(), null, this);
  }

  @Override
  public void installWatcher(JComponent c) {
    super.installWatcher(c);
    addSettingsEditorListener(editor -> SwingUtilities.invokeLater(() -> UIUtil.setupEnclosingDialogBounds(c)));
  }

  protected void initFragments(Collection<? extends SettingsEditorFragment<Settings, ?>> fragments) {
  }
}
