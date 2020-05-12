// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.Nls;
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
  private final Predicate<Settings> myInitialSelection;

  public SettingsEditorFragment(String id,
                                @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                @Nls(capitalization = Nls.Capitalization.Sentence) String group,
                                C component,
                                int commandLinePosition,
                                BiConsumer<Settings, C> reset,
                                BiConsumer<Settings, C> apply,
                                Predicate<Settings> initialSelection) {
    myId = id;
    myName = name;
    myGroup = group;
    myComponent = component;
    myReset = reset;
    myApply = apply;
    myCommandLinePosition = commandLinePosition;
    myInitialSelection = initialSelection;
  }

  public SettingsEditorFragment(String id,
                                @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                @Nls(capitalization = Nls.Capitalization.Sentence) String group,
                                C component,
                                BiConsumer<Settings, C> reset, BiConsumer<Settings, C> apply,
                                Predicate<Settings> initialSelection)  {
    this(id, name, group, component, 0, reset, apply, initialSelection);
  }

  public static <S> SettingsEditorFragment<S, ?> create(String id, String name, String group, Component<? super S> component) {
    return new SettingsEditorFragment<>(id, name, group, (JComponent)component,
                                        (settings, c) -> component.reset(settings),
                                        (settings, c) -> component.apply(settings),
                                        s -> component.isVisible(s));
  }

  public static <Settings> SettingsEditorFragment<Settings, JButton> createTag(String id, String name, String group,
                                                                               Predicate<Settings> getter, BiConsumer<Settings, Boolean> setter) {
    Ref<SettingsEditorFragment<Settings, JButton>> ref = new Ref<>();
    TagButton button = new TagButton(name, () -> ref.get().setSelected(false));
    SettingsEditorFragment<Settings, JButton> fragment = new SettingsEditorFragment<Settings, JButton>(id, name, group, button,
                                                                                                       (settings, label) -> label.setVisible(getter.test(settings)),
                                                                                                       (settings, label) -> setter.accept(settings, label.isVisible()),
                                                                                                       getter) {

      @Override
      public boolean isTag() {
        return true;
      }
    };
    Disposer.register(fragment, button);
    ref.set(fragment);
    return fragment;
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

  public boolean isSelected() {
    return myComponent.isVisible();
  }

  public boolean isInitiallyVisible(Settings settings) {
    return myInitialSelection.test(settings);
  }

  public void setSelected(boolean selected) {
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
    myComponent.setVisible(isSelected());
    return myComponent;
  }

  @Override
  public String toString() {
    return myId + " " + myName;
  }
}
