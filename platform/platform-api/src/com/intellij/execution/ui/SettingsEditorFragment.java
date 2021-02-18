// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class SettingsEditorFragment<Settings, C extends JComponent> extends SettingsEditor<Settings> {

  private final String myId;
  private final @Nls String myName;
  private final @Nls String myGroup;
  protected C myComponent;
  private final BiConsumer<? super Settings, ? super C> myReset;
  private final BiConsumer<? super Settings, ? super C> myApply;
  private @Nullable Function<Settings, List<ValidationInfo>> myValidation;
  private final int myCommandLinePosition;
  private final Predicate<? super Settings> myInitialSelection;
  private @Nullable @Nls String myHint;
  private @Nullable JComponent myHintComponent;
  private @Nullable @Nls String myActionHint;
  private @Nullable @Nls String myActionDescription;
  private @Nullable String myConfigId; // for FUS
  private @Nullable Function<? super C, ? extends JComponent> myEditorGetter;
  private boolean myRemovable = true;
  private boolean myCanBeHidden;

  public SettingsEditorFragment(String id,
                                @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                @Nls(capitalization = Nls.Capitalization.Title) String group,
                                C component,
                                int commandLinePosition,
                                BiConsumer<? super Settings, ? super C> reset,
                                BiConsumer<? super Settings, ? super C> apply,
                                Predicate<? super Settings> initialSelection) {
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
                                @Nls(capitalization = Nls.Capitalization.Title) String group,
                                C component,
                                BiConsumer<? super Settings, ? super C> reset,
                                BiConsumer<? super Settings, ? super C> apply,
                                Predicate<? super Settings> initialSelection) {
    this(id, name, group, component, 0, reset, apply, initialSelection);
  }

  public static <S> SettingsEditorFragment<S, ?> createWrapper(String id,
                                                               @Nls String name,
                                                               @Nls String group,
                                                               @NotNull SettingsEditor<S> inner,
                                                               Predicate<? super S> initialSelection) {
    JComponent component = inner.getComponent();
    SettingsEditorFragment<S, JComponent> fragment = new SettingsEditorFragment<>(id, name, group, component,
                                                                                  (settings, c) -> inner.resetFrom(settings),
                                                                                  (settings, c) -> {
                                                                                    try {
                                                                                      inner.applyTo(settings);
                                                                                    }
                                                                                    catch (ConfigurationException e) {
                                                                                      throw new RuntimeException(e);
                                                                                    }
                                                                                  },
                                                                                  initialSelection);
    Disposer.register(fragment, inner);
    return fragment;
  }

  public static <Settings> SettingsEditorFragment<Settings, TagButton> createTag(String id, @Nls String name, @Nls String group,
                                                                         Predicate<? super Settings> getter,
                                                                         BiConsumer<? super Settings, ? super Boolean> setter) {
    Ref<SettingsEditorFragment<Settings, ?>> ref = new Ref<>();
    TagButton tagButton = new TagButton(name, (e) -> {
      ref.get().setSelected(false);
      ref.get().logChange(false, e);
    });
    SettingsEditorFragment<Settings, TagButton> fragment = new SettingsEditorFragment<>(id, name, group, tagButton,
                                                                                                    (settings, button) -> button.setVisible(getter.test(settings)),
                                                                                                    (settings, button) -> setter.accept(settings, button.isVisible()),
                                                                                                    getter) {

      @Override
      public boolean isTag() {
        return true;
      }
    };
    Disposer.register(fragment, tagButton);
    ref.set(fragment);
    return fragment;
  }

  public String getId() {
    return myId;
  }

  @Nls
  public String getName() {
    return myName;
  }

  @Nls
  public String getGroup() {
    return myGroup;
  }

  public C component() {
    return myComponent;
  }

  public JComponent[] getAllComponents() {
    return new JComponent[] { component() };
  }

  public boolean isTag() { return false; }

  @Nullable
  public ActionGroup getCustomActionGroup() {
    return null;
  }

  public boolean isSelected() {
    return myComponent.isVisible();
  }

  public boolean isInitiallyVisible(Settings settings) {
    return myInitialSelection.test(settings);
  }

  public boolean isRemovable() {
    return myRemovable;
  }

  public void setRemovable(boolean removable) {
    myRemovable = removable;
  }

  public void setValidation(@Nullable Function<Settings, List<ValidationInfo>> validation) {
    myValidation = validation;
  }

  protected void validate(Settings s) {
    if (myValidation == null) return;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<ValidationInfo> infos = myValidation.apply(s);
      if (infos.isEmpty()) return;
      UIUtil.invokeLaterIfNeeded(() -> {
        if (Disposer.isDisposed(this)) return;
        for (ValidationInfo info : infos) {
          JComponent component = info.component;
          if (component == null) continue;
          Optional<ComponentValidator> optional = ComponentValidator.getInstance(component);
          ComponentValidator validator;
          if (optional.isEmpty()) {
            validator = new ComponentValidator(this);
            validator.installOn(component);
          }
          else {
            validator = optional.get();
          }
          validator.updateInfo(info.message.isEmpty() ? null : info);
        }
      });
    });
  }

  /**
   * Can be hidden by user even if {@link #isInitiallyVisible(Object)} returns true
   */
  public boolean isCanBeHidden() {
    return myCanBeHidden;
  }

  public void setCanBeHidden(boolean canBeHidden) {
    myCanBeHidden = canBeHidden;
  }

  public void setSelected(boolean selected) {
    myComponent.setVisible(selected);
    if (myHintComponent != null) {
      myHintComponent.setVisible(selected);
    }
    fireEditorStateChanged();
  }

  public void toggle(boolean selected, @Nullable AnActionEvent e) {
    setSelected(selected);
    if (selected) {
      myComponent.scrollRectToVisible(new Rectangle(new Point(0, 50), myComponent.getPreferredSize()));
    }
    logChange(selected, e);
  }

  protected void logChange(boolean selected, @Nullable AnActionEvent e) {
    if (selected) {
      FragmentStatisticsService.getInstance().logOptionModified(getProject(), getId(), myConfigId, e);
    }
    else {
      FragmentStatisticsService.getInstance().logOptionRemoved(getProject(), getId(), myConfigId, e);
    }
  }

  private Project getProject() {
    return DataManager.getInstance().getDataContext(myComponent).getData(PlatformDataKeys.PROJECT_CONTEXT);
  }

  public void setEditorGetter(@Nullable Function<? super C, ? extends JComponent> editorGetter) {
    myEditorGetter = editorGetter;
  }

  public JComponent getEditorComponent() {
    JComponent component = component();
    if (myEditorGetter != null) return myEditorGetter.apply(component());
    if (component instanceof LabeledComponent) {
      component = ((LabeledComponent<?>)component).getComponent();
    }
    else if (component instanceof  TagButton) {
      return ((TagButton)component).myButton;
    }
    return component instanceof ComponentWithBrowseButton ? ((ComponentWithBrowseButton<?>)component).getChildComponent() : component;
  }

  public int getCommandLinePosition() {
    return myCommandLinePosition;
  }

  public int getMenuPosition() { return 0; }

  @Override
  protected void resetEditorFrom(@NotNull Settings s) {
    myReset.accept(s, myComponent);
  }

  @Override
  protected void applyEditorTo(@NotNull Settings s) {
    myApply.accept(s, myComponent);
    validate(s);
  }

  @Override
  protected @NotNull JComponent createEditor() {
    myComponent.setVisible(isSelected());
    return myComponent;
  }

  public List<SettingsEditorFragment<Settings, ?>> getChildren() {
    return Collections.emptyList();
  }

  public @Nullable @Nls String getChildrenGroupName() {
    return null;
  }

  public @Nullable @NlsActions.ActionDescription String getActionHint() {
    return myActionHint;
  }

  public void setActionDescription(@Nullable @Nls String actionDescription) {
    myActionDescription = actionDescription;
  }

  public @Nullable @Nls String getActionDescription() {
    return myActionDescription;
  }

  public void setActionHint(@Nullable @Nls String hint) {
    myActionHint = hint;
  }

  public @Nullable String getHint(@Nullable JComponent component) {
    return myHint;
  }

  public void setHint(@Nullable @Nls String hint) {
    myHint = hint;
  }

  public void setConfigId(@Nullable String configId) {
    myConfigId = configId;
  }

  public @Nullable JComponent getHintComponent() {
    if (myHintComponent == null && myHint != null) {
      JLabel comment = ComponentPanelBuilder.createNonWrappingCommentComponent(myHint);
      comment.setFocusable(false);
      myHintComponent = LabeledComponent.create(comment, "", BorderLayout.WEST);
    }
    return myHintComponent;
  }

  @Override
  public String toString() {
    return myId + " " + myName;
  }
}
