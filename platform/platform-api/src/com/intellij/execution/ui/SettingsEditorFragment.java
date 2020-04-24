// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class SettingsEditorFragment<Settings, C extends JComponent> extends SettingsEditor<Settings> {

  /**
   * Should be implemented by a JComponent
   */
  public interface Component<Settings> {
    void reset(Settings s);
    void apply(Settings s);
    boolean isVisible(Settings s);
  }

  private final String myId;
  private final String myName;
  private final String myGroup;
  private final C myComponent;
  private final BiConsumer<Settings, C> myReset;
  private final BiConsumer<Settings, C> myApply;
  private final int myCommandLinePosition;
  private final Predicate<Settings> myInitialVisibility;

  public SettingsEditorFragment(String id,
                                String name,
                                String group,
                                C component,
                                int commandLinePosition,
                                BiConsumer<Settings, C> reset,
                                BiConsumer<Settings, C> apply,
                                Predicate<Settings> initialVisibility) {
    myId = id;
    myName = name;
    myGroup = group;
    myComponent = component;
    myReset = reset;
    myApply = apply;
    myCommandLinePosition = commandLinePosition;
    myInitialVisibility = initialVisibility;
  }

  public SettingsEditorFragment(String id, String name, String group, C component,
                                BiConsumer<Settings, C> reset, BiConsumer<Settings, C> apply,
                                Predicate<Settings> initialVisibility)  {
    this(id, name, group, component, -1, reset, apply, initialVisibility);
  }

  public static <S> SettingsEditorFragment<S, ?> create(String id, String name, String group, Component<? super S> component) {
    return new SettingsEditorFragment<>(id, name, group, (JComponent)component,
                                        (settings, c) -> component.reset(settings),
                                        (settings, c) -> component.apply(settings),
                                        s -> component.isVisible(s));
  }

  public String getId() {
    return myId;
  }

  public String getName() {
    return myName;
  }

  public String getGroup() {
    return myGroup;
  }

  public boolean isTag() { return false; }

  public boolean isVisible() {
    return myComponent.isVisible();
  }

  public boolean isInitiallyVisible(Settings settings) {
    return myInitialVisibility.test(settings);
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

  @Override
  public String toString() {
    return myId + " " + myName;
  }
}
