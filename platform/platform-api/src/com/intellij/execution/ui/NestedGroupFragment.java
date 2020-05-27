// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditorListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.function.Predicate;

public abstract class NestedGroupFragment<S extends FragmentedSettings> extends SettingsEditorFragment<S, JComponent> {

  private final List<SettingsEditorFragment<S, ?>> myChildren;
  private JComponent myGroupComponent;

  protected NestedGroupFragment(String id,
                                @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                @Nls(capitalization = Nls.Capitalization.Sentence) String group,
                                Predicate<S> initialSelection) {
    super(id, name, group, null, null, null, initialSelection);
    myChildren = createChildren();
    SettingsEditorListener<S> listener = editor -> { updateVisibility(); fireEditorStateChanged(); };
    for (SettingsEditorFragment<S, ?> child : myChildren) {
      Disposer.register(this, child);
      child.addSettingsEditorListener(listener);
    }
  }

  @Override
  public final List<SettingsEditorFragment<S, ?>> getChildren() {
    return myChildren;
  }

  @Override
  public void setSelected(boolean selected) {
    super.setSelected(selected);
    updateVisibility();
  }

  private void updateVisibility() {
    myGroupComponent.setVisible(isSelected() || ContainerUtil.exists(getChildren(), fragment -> fragment.isSelected()));
  }

  @Override
  protected void resetEditorFrom(@NotNull S s) {
    for (SettingsEditorFragment<S, ?> child : getChildren()) {
      child.resetEditorFrom(s);
    }
    updateVisibility();
  }

  @Override
  protected void applyEditorTo(@NotNull S s) throws ConfigurationException {
    for (SettingsEditorFragment<S, ?> child : getChildren()) {
      child.applyEditorTo(s);
    }
  }

  protected abstract List<SettingsEditorFragment<S, ?>> createChildren();

    @Override
  protected @NotNull JComponent createEditor() {
     myGroupComponent = new FragmentedSettingsBuilder<>(getChildren(), this).createCompoundEditor();
     updateVisibility();
     return myGroupComponent;
  }
}
