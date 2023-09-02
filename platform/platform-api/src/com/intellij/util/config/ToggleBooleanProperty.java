// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.config;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ToggleBooleanProperty extends ToggleAction {
  private final AbstractProperty.AbstractPropertyContainer myProperties;
  private final AbstractProperty<Boolean> myProperty;

  public ToggleBooleanProperty(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon, AbstractProperty.AbstractPropertyContainer properties, BooleanProperty property) {
    super(text, description, icon);
    myProperties = properties;
    myProperty = property;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return myProperty.get(myProperties).booleanValue();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    myProperty.set(myProperties, Boolean.valueOf(state));
  }

  protected AbstractProperty.AbstractPropertyContainer getProperties() {
    return myProperties;
  }

  protected AbstractProperty<Boolean> getProperty() {
    return myProperty;
  }

  public abstract static class Disablable extends ToggleBooleanProperty {
    public Disablable(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon, AbstractProperty.AbstractPropertyContainer properties, BooleanProperty property) {
      super(text, description, icon, properties, property);
    }

    protected abstract boolean isEnabled();
    protected abstract boolean isVisible();

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(isEnabled());
      e.getPresentation().setVisible(isVisible());
    }
  }
}
