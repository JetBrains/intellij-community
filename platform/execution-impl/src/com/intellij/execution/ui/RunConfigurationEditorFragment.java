// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public abstract class RunConfigurationEditorFragment<Settings, C extends JComponent> extends SettingsEditorFragment<Settings, C>{
  public RunConfigurationEditorFragment(String id,
                                        @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                        @Nls(capitalization = Nls.Capitalization.Title) String group,
                                        C component,
                                        int commandLinePosition) {
    super(id, name, group, component, commandLinePosition, (settings, c) -> {}, (settings, c) -> {}, settings -> false);
  }

  public abstract void resetEditorFrom(@NotNull RunnerAndConfigurationSettingsImpl s);

  public abstract void applyEditorTo(@NotNull RunnerAndConfigurationSettingsImpl s);

  public static <Settings> SettingsEditorFragment<Settings, ?> createSettingsTag(String id, String name, String group,
                                                                                 Predicate<RunnerAndConfigurationSettingsImpl> getter,
                                                                                 BiConsumer<RunnerAndConfigurationSettingsImpl, Boolean> setter,
                                                                                 int menuPosition) {
    Ref<SettingsEditorFragment<?, ?>> ref = new Ref<>();
    TagButton button = new TagButton(name, () -> ref.get().setSelected(false));
    RunConfigurationEditorFragment<Settings, ?> fragment = new RunConfigurationEditorFragment<Settings, JComponent>(id, name, group, button, 0) {

      @Override
      public void resetEditorFrom(@NotNull RunnerAndConfigurationSettingsImpl s) {
        button.setVisible(getter.test(s));
      }

      @Override
      public void applyEditorTo(@NotNull RunnerAndConfigurationSettingsImpl s) {
        setter.accept(s, button.isVisible());
      }

      @Override
      public boolean isInitiallyVisible(Settings settings) {
        return isSelected();
      }

      @Override
      public boolean isTag() {
        return true;
      }

      @Override
      public int getMenuPosition() {
        return menuPosition;
      }
    };
    Disposer.register(fragment, button);
    ref.set(fragment);
    return fragment;
  }
}
