// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.function.Predicate;

public abstract class NestedGroupFragment<S extends FragmentedSettings> extends SettingsEditorFragment<S, JComponent> {

  private final NotNullLazyValue<List<SettingsEditorFragment<S, ?>>> myChildren = NotNullLazyValue.createValue(() -> {
    List<SettingsEditorFragment<S, ?>> children = createChildren();
    for (SettingsEditorFragment<S, ?> child : children) {
      Disposer.register(this, child);
      child.addSettingsEditorListener(editor -> {
        if (child.isSelected()) {
          setSelected(true);
        }
        updateVisibility();
        fireEditorStateChanged();
      });
    }
    return children;
  });

  private JComponent myGroupComponent;

  protected NestedGroupFragment(String id,
                                @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                @Nls(capitalization = Nls.Capitalization.Title) String group,
                                Predicate<? super S> initialSelection) {
    super(id, name, group, null, null, null, initialSelection);
  }

  @Override
  public final List<SettingsEditorFragment<S, ?>> getChildren() {
    return myChildren.getValue();
  }

  @Override
  public String getChildrenGroupName() {
    return getName();
  }

  @Override
  public void setSelected(boolean selected) {
    super.setSelected(selected);
    updateVisibility();
  }

  @Override
  public boolean isSelected() {
    return ContainerUtil.exists(getChildren(), fragment -> fragment.isSelected());
  }

  @Override
  public boolean isInitiallyVisible(S s) {
    return super.isInitiallyVisible(s) || ContainerUtil.exists(getChildren(), fragment -> fragment.isInitiallyVisible(s));
  }

  private void updateVisibility() {
    myGroupComponent.setVisible(isSelected());
  }

  @Override
  protected void resetEditorFrom(@NotNull S s) {
    for (SettingsEditorFragment<S, ?> child : getChildren()) {
      child.resetEditorFrom(s);
    }
    updateVisibility();
  }

  @Override
  protected void applyEditorTo(@NotNull S s) {
    for (SettingsEditorFragment<S, ?> child : getChildren()) {
      child.applyEditorTo(s);
    }
  }

  protected abstract List<SettingsEditorFragment<S, ?>> createChildren();

  protected @NotNull FragmentedSettingsBuilder<S> getBuilder() {
    return new FragmentedSettingsBuilder<>(getChildren(), this, this);
  }

  @Override
  protected @NotNull JComponent createEditor() {
    var builder = getBuilder();
    myGroupComponent = builder.createCompoundEditor();
    if (myComponent == null) myComponent = myGroupComponent;
    updateVisibility();
    return myGroupComponent;
  }
}
