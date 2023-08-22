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

public abstract class RunConfigurationEditorFragment<Settings, C extends JComponent> extends SettingsEditorFragment<Settings, C> {

  private RunnerAndConfigurationSettingsImpl mySettings;
  private final Predicate<? super RunnerAndConfigurationSettingsImpl> myInitialVisibility;

  public RunConfigurationEditorFragment(String id,
                                        @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                        @Nls(capitalization = Nls.Capitalization.Title) String group,
                                        C component,
                                        int commandLinePosition,
                                        Predicate<? super RunnerAndConfigurationSettingsImpl> initialVisibility) {
    super(id, name, group, component, commandLinePosition, (settings, c) -> {}, (settings, c) -> {}, settings -> false);
    myInitialVisibility = initialVisibility;
  }

  public void resetEditorFrom(@NotNull RunnerAndConfigurationSettingsImpl s) {
    mySettings = s;
    doReset(s);
  }

  protected abstract void doReset(@NotNull RunnerAndConfigurationSettingsImpl s);

  public abstract void applyEditorTo(@NotNull RunnerAndConfigurationSettingsImpl s);

  @Override
  public boolean isInitiallyVisible(Settings settings) {
    return myInitialVisibility.test(mySettings);
  }

  public static <Settings> SettingsEditorFragment<Settings, ?> createSettingsTag(String id, @Nls String name, @Nls String group,
                                                                                 @NotNull Predicate<? super RunnerAndConfigurationSettingsImpl> getter,
                                                                                 @NotNull BiConsumer<? super RunnerAndConfigurationSettingsImpl, ? super Boolean> setter,
                                                                                 int menuPosition) {
    Ref<SettingsEditorFragment<?, ?>> ref = new Ref<>();
    TagButton button = new TagButton(name, (e) -> {
      ref.get().setSelected(false);
      ref.get().logChange(false, e);
    });
    RunConfigurationEditorFragment<Settings, ?> fragment = new RunConfigurationEditorFragment<>(id, name, group, button, 0, getter) {
      @Override
      protected void disposeEditor() {
        Disposer.dispose(myComponent);
      }

      @Override
      public void doReset(@NotNull RunnerAndConfigurationSettingsImpl s) {
        button.setVisible(getter.test(s));
      }

      @Override
      public void applyEditorTo(@NotNull RunnerAndConfigurationSettingsImpl s) {
        setter.accept(s, button.isVisible());
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
    ref.set(fragment);
    return fragment;
  }
}
