// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.BiConsumer;

public class SettingsEditorFragment<Settings, C extends JComponent> extends SettingsEditor<Settings> {

  /**
   * Should be implemented by a JComponent
   */
  public interface Component<Settings> {
    void reset(Settings s);
    void apply(Settings s);
  }

  private final String myName;
  private final C myComponent;
  private final BiConsumer<Settings, C> myReset;
  private final BiConsumer<Settings, C> myApply;
  private final int myCommandLinePosition;

  public SettingsEditorFragment(String name, C component, int commandLinePosition, BiConsumer<Settings, C> reset, BiConsumer<Settings, C> apply) {
    myName = name;
    myComponent = component;
    myReset = reset;
    myApply = apply;
    myCommandLinePosition = commandLinePosition;
  }

  public SettingsEditorFragment(String name, C component, BiConsumer<Settings, C> reset, BiConsumer<Settings, C> apply)  {
    this(name, component, -1, reset, apply);
  }

  public static <S> SettingsEditorFragment<S, ?> create(String name, Component<? super S> component) {
    return new SettingsEditorFragment<>(name, (JComponent)component,
                                        (settings, c) -> component.reset(settings),
                                        (settings, c) -> component.apply(settings));
  }

  public String getName() {
    return myName;
  }

  public boolean isTag() { return false; }

  public boolean isVisible() {
    return myComponent.isVisible();
  }

  public void setVisible(boolean selected) {
    myComponent.setVisible(selected);
    fireEditorStateChanged();
  }

  public int getCommandLinePosition() {
    return myCommandLinePosition;
  }

  @Override
  protected void resetEditorFrom(@NotNull Settings s) {
    myReset.accept(s, myComponent);
  }

  @Override
  protected void applyEditorTo(@NotNull Settings s) throws ConfigurationException {
    myApply.accept(s, myComponent);
  }

  @Override
  protected @NotNull JComponent createEditor() {
    myComponent.setVisible(isVisible());
    return myComponent;
  }
}
