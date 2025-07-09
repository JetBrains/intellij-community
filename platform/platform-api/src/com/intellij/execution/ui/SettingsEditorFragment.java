// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A single setting fragment that the user can enable and configure.
 * <p>
 * Fragments whose {@link #getGroup()} returns an identical string are grouped together in the UI.
 * <p>
 * Can be nested with other related fragments in a {@link NestedGroupFragment}.
 * 
 * @see FragmentedSettingsEditor
 */
public class SettingsEditorFragment<Settings, C extends JComponent> extends SettingsEditor<Settings> {
  private final String myId;
  private final @Nls String myName;
  private final @Nls String myGroup;
  protected C myComponent;
  private final BiConsumer<? super Settings, ? super C> onResetEditorFromSettings;
  private final BiConsumer<? super Settings, ? super C> onApplyEditorToSettings;
  private final List<Function<? super Settings, List<ValidationInfo>>> myValidation = new ArrayList<>();
  private final @NotNull SettingsEditorFragmentType myType;
  private final int myPriority;
  private final Predicate<? super Settings> myInitialSelection;
  private @Nullable @Nls String myHint;
  private @Nullable JComponent myHintComponent;
  private @Nullable @Nls String myActionHint;
  private @Nullable @Nls String myActionDescription;
  private @Nullable String myConfigId; // for FUS
  private @Nullable Function<? super C, ? extends JComponent> myEditorGetter;
  private boolean myRemovable = true;
  private boolean myCanBeHidden = false;

  private boolean isSelected;

  public SettingsEditorFragment(String id,
                                @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                @Nls(capitalization = Nls.Capitalization.Title) String group,
                                C component,
                                int priority,
                                @NotNull SettingsEditorFragmentType type,
                                BiConsumer<? super Settings, ? super C> resetEditorFromSettings,
                                BiConsumer<? super Settings, ? super C> applyEditorToSettings,
                                Predicate<? super Settings> initialSelection) {
    myId = id;
    myName = name;
    myGroup = group;
    myComponent = component;
    onResetEditorFromSettings = resetEditorFromSettings;
    onApplyEditorToSettings = applyEditorToSettings;
    myPriority = priority;
    myType = type;
    myInitialSelection = initialSelection;
  }

  public SettingsEditorFragment(String id,
                                @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                @Nls(capitalization = Nls.Capitalization.Title) String group,
                                C component,
                                @NotNull SettingsEditorFragmentType type,
                                BiConsumer<? super Settings, ? super C> resetEditorFromSettings,
                                BiConsumer<? super Settings, ? super C> applyEditorToSettings,
                                Predicate<? super Settings> initialSelection) {
    this(id, name, group, component, 0, type, resetEditorFromSettings, applyEditorToSettings, initialSelection);
  }

  public SettingsEditorFragment(String id,
                                @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                @Nls(capitalization = Nls.Capitalization.Title) String group,
                                C component,
                                int commandLinePosition,
                                BiConsumer<? super Settings, ? super C> resetEditorFromSettings,
                                BiConsumer<? super Settings, ? super C> applyEditorToSettings,
                                Predicate<? super Settings> initialSelection) {
    this(id, name, group, component, commandLinePosition, getType(component, commandLinePosition), resetEditorFromSettings, applyEditorToSettings, initialSelection);
  }

  private static @NotNull SettingsEditorFragmentType getType(JComponent component, int commandLinePosition) {
    return component instanceof TagButton ? SettingsEditorFragmentType.TAG :
           commandLinePosition == -2 ? SettingsEditorFragmentType.BEFORE_RUN :
           commandLinePosition == -1 ? SettingsEditorFragmentType.HEADER :
           commandLinePosition == 0 ? SettingsEditorFragmentType.EDITOR :
           SettingsEditorFragmentType.COMMAND_LINE;
  }

  public SettingsEditorFragment(String id,
                                @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                @Nls(capitalization = Nls.Capitalization.Title) String group,
                                C component,
                                BiConsumer<? super Settings, ? super C> resetEditorFromSettings,
                                BiConsumer<? super Settings, ? super C> applyEditorToSettings,
                                Predicate<? super Settings> initialSelection) {
    this(id, name, group, component, 0, SettingsEditorFragmentType.EDITOR, resetEditorFromSettings, applyEditorToSettings, initialSelection);
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

  /**
   * Creates a new tag button with {@code name} as its text.
   * <p>
   * A tag button is a gray-ish, rounded button that usually signifies that some option from under the "Modify options" dropdown is enabled.
   */
  public static <Settings> SettingsEditorFragment<Settings, TagButton> createTag(String id, @Nls String name, @Nls String group,
                                                                         Predicate<? super Settings> getter,
                                                                         BiConsumer<? super Settings, ? super Boolean> setter) {
    Ref<SettingsEditorFragment<Settings, ?>> ref = new Ref<>();
    TagButton tagButton = new TagButton(name, (e) -> {
      ref.get().setSelected(false);
      ref.get().logChange(false, e);
    });
    SettingsEditorFragment<Settings, TagButton> fragment = new SettingsEditorFragment<>(
      id, name, group, tagButton, SettingsEditorFragmentType.TAG,
      (settings, button) -> button.setVisible(getter.test(settings)),
      (settings, button) -> setter.accept(settings, button.isVisible()),
      getter
    );
    Disposer.register(fragment, tagButton);
    ref.set(fragment);
    return fragment;
  }

  public String getId() {
    return myId;
  }

  public @Nls String getName() {
    return myName;
  }

  public @Nls String getGroup() {
    return myGroup;
  }

  public C component() {
    return myComponent;
  }

  public JComponent[] getAllComponents() {
    return new JComponent[]{component()};
  }

  public boolean isTag() {
    return SettingsEditorFragmentType.TAG == myType;
  }

  public boolean isCommandLine() {
    return SettingsEditorFragmentType.COMMAND_LINE == myType;
  }

  public boolean isHeader() {
    return SettingsEditorFragmentType.HEADER == myType;
  }

  public boolean isBeforeRun() {
    return SettingsEditorFragmentType.BEFORE_RUN == myType;
  }

  public boolean isEditor() {
    return SettingsEditorFragmentType.EDITOR == myType;
  }

  public int getPriority() {
    return myPriority;
  }

  public @Nullable ActionGroup getCustomActionGroup() {
    return null;
  }

  public boolean isSelected() {
    return isSelected;
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

  public void setValidation(@Nullable Function<? super Settings, List<ValidationInfo>> validation) {
    myValidation.clear();
    if (validation != null) {
      myValidation.add(validation);
    }
  }

  private @NotNull SettingsEditorFragment<Settings, C> addValidation(@NotNull Function<? super Settings, ValidationInfo> validation) {
    myValidation.add(it -> Collections.singletonList(validation.apply(it)));
    return this;
  }

  public @NotNull SettingsEditorFragment<Settings, C> addValidation(
    @NotNull ThrowableConsumer<? super Settings, ? extends ConfigurationException> validation
  ) {
    return addValidation(settings -> {
      try {
        validation.consume(settings);
        return new ValidationInfo("", getEditorComponent());
      }
      catch (ConfigurationException exception) {
        return new ValidationInfo(exception.getMessageHtml(), getEditorComponent());
      }
    });
  }

  protected void validate(Settings s) {
    if (myValidation.isEmpty()) return;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<ValidationInfo> infos = ContainerUtil.flatMap(myValidation, it -> ReadAction.nonBlocking(() -> it.apply(s)).executeSynchronously());
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
   * Can be hidden by user even if {@link #isInitiallyVisible isInitiallyVisible()} returns true
   */
  public boolean isCanBeHidden() {
    return myCanBeHidden;
  }

  public void setCanBeHidden(boolean canBeHidden) {
    myCanBeHidden = canBeHidden;
  }

  public void setSelected(boolean selected) {
    isSelected = selected;
    myComponent.setVisible(selected);
    if (myHintComponent != null) {
      myHintComponent.setVisible(selected);
    }
    fireEditorStateChanged();
  }

  public void toggle(boolean selected, @Nullable AnActionEvent e) {
    boolean changed = isSelected() != selected;
    setSelected(selected);
    if (selected) {
      JScrollPane scrollpane = UIUtil.getParentOfType(JScrollPane.class, myComponent);
      if (scrollpane != null) {
        scrollpane.validate();  // should be validated beforehand to make scrollRectToVisible() work correctly
      }
      myComponent.scrollRectToVisible(new Rectangle(new Point(0, 50), myComponent.getPreferredSize()));
    }
    if (changed) {
      logChange(selected, e);
    }
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
    return DataManager.getInstance().getDataContext(myComponent).getData(PlatformCoreDataKeys.PROJECT_CONTEXT);
  }

  public void setEditorGetter(@Nullable Function<? super C, ? extends JComponent> editorGetter) {
    myEditorGetter = editorGetter;
  }

  public JComponent getEditorComponent() {
    C component = component();
    if (myEditorGetter != null) {
      return myEditorGetter.apply(component);
    }
    return getEditorComponent(component);
  }

  private static JComponent getEditorComponent(JComponent component) {
    if (component instanceof LabeledComponent) {
      component = ((LabeledComponent<?>)component).getComponent();
    }

    if (component instanceof TagButton) {
      return ((TagButton)component).myButton;
    }
    if (component instanceof ComponentWithBrowseButton) {
      return ((ComponentWithBrowseButton<?>)component).getChildComponent();
    }
    if (component instanceof RawCommandLineEditor) {
      return ((RawCommandLineEditor)component).getEditorField();
    }
    return component;
  }

  public int getMenuPosition() { return 0; }

  @Override
  protected void resetEditorFrom(@NotNull Settings s) {
    onResetEditorFromSettings.accept(s, myComponent);
  }

  @Override
  protected void applyEditorTo(@NotNull Settings s) {
    onApplyEditorToSettings.accept(s, myComponent);
  }
  
  @ApiStatus.Internal
  public static <S> void applyEditorTo(@NotNull SettingsEditorFragment<S, ?> fragment, @NotNull S s) {
    fragment.applyEditorTo(s);
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
    myActionHint = hint == null ? null : StringUtil.removeHtmlTags(hint, true);
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