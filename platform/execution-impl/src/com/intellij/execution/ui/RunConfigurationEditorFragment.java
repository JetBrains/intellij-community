// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    Ref<TagButtonRunConfigurationEditorFragment<Settings>> ref = new Ref<>();
    TagButton button = new TagButton(name, (e) -> {
      ref.get().setSelected(false);
      ref.get().logFragmentChange(false, e);
    });
    TagButtonRunConfigurationEditorFragment<Settings> fragment =
      new TagButtonRunConfigurationEditorFragment<>(id, name, group, button, getter, setter, menuPosition);
    ref.set(fragment);
    return fragment;
  }

  private static class TagButtonRunConfigurationEditorFragment<Settings> extends RunConfigurationEditorFragment<Settings, TagButton> {
    private final @NotNull Predicate<? super RunnerAndConfigurationSettingsImpl> myGetter;
    private final @NotNull BiConsumer<? super RunnerAndConfigurationSettingsImpl, ? super Boolean> mySetter;
    private final int myMenuPosition;

    private TagButtonRunConfigurationEditorFragment(String id,
                                                    @Nls String name,
                                                    @Nls String group,
                                                    TagButton button,
                                                    @NotNull Predicate<? super RunnerAndConfigurationSettingsImpl> getter,
                                                    @NotNull BiConsumer<? super RunnerAndConfigurationSettingsImpl, ? super Boolean> setter,
                                                    int menuPosition) {
      super(id, name, group, button, 0, getter);
      myGetter = getter;
      mySetter = setter;
      myMenuPosition = menuPosition;
    }

    void logFragmentChange(boolean selected, @Nullable AnActionEvent e) {
      logChange(selected, e);
    }

    @Override
    protected void disposeEditor() {
      Disposer.dispose(myComponent);
    }

    @Override
    public void doReset(@NotNull RunnerAndConfigurationSettingsImpl s) {
      component().setVisible(myGetter.test(s));
    }

    @Override
    public void applyEditorTo(@NotNull RunnerAndConfigurationSettingsImpl s) {
      mySetter.accept(s, component().isVisible());
    }

    @Override
    public boolean isTag() {
      return true;
    }

    @Override
    public int getMenuPosition() {
      return myMenuPosition;
    }
  }
}
