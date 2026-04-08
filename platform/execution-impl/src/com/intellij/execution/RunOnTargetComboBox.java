// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.target.LanguageRuntimeType;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentType;
import com.intellij.execution.target.TargetEnvironmentWizard;
import com.intellij.execution.target.TargetEnvironmentsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultComboBoxModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public final class RunOnTargetComboBox extends ComboBox<Item> {
  public static final Logger LOGGER = Logger.getInstance(RunOnTargetComboBox.class);
  private static final int SAVED_TARGETS_SECTION_INDEX = 1;
  private final @NotNull Project myProject;
  private @Nullable LanguageRuntimeType<?> myDefaultRuntimeType;
  private boolean hasSavedTargets = false;

  public RunOnTargetComboBox(@NotNull Project project) {
    super();
    setModel(new MyModel());
    myProject = project;
    setRenderer(RunOnTargetComboBoxKt.createItemRenderer(() -> hasSavedTargets, null));
    addActionListener(e -> validateSelectedTarget());
  }

  public void initModel(List<? extends TargetEnvironmentConfiguration> configs) {
    setRenderer(RunOnTargetComboBoxKt.createItemRenderer(() -> hasSavedTargets,
                                                         TargetEnvironmentsManager.getInstance(myProject).getDefaultTarget()));

    hasSavedTargets = false;
    MyModel model = (MyModel)getModel();
    model.removeAllElements();
    model.addElement(null);

    if (!configs.isEmpty()) {
      addSavedTargetsSection();

      for (TargetEnvironmentConfiguration config : configs) {
        ((MyModel)getModel()).addElement(new SavedTarget(config));
      }
    }

    Collection<Type<?>> types = new ArrayList<>();
    for (TargetEnvironmentType<?> type : TargetEnvironmentType.getTargetTypesForRunConfigurations()) {
      if (type.isSystemCompatible() && type.providesNewWizard(myProject, myDefaultRuntimeType)) {
        var separator = types.isEmpty() ? ExecutionBundle.message("run.on.targets.label.new.targets") : null;
        types.add(new Type<>(type, separator));
      }
    }
    if (!types.isEmpty()) {
      for (Type<?> type : types) {
        model.addElement(type);
      }
    }
  }

  public void setDefaultLanguageRuntimeType(@Nullable LanguageRuntimeType<?> defaultLanguageRuntimeType) {
    myDefaultRuntimeType = defaultLanguageRuntimeType;
  }

  public @Nullable LanguageRuntimeType<?> getDefaultLanguageRuntimeType() {
    return myDefaultRuntimeType;
  }

  private void addSavedTargetsSection() {
    if (!hasSavedTargets) {
      hasSavedTargets = true;
      ((MyModel)getModel()).insertElementAt(new LocalTarget(ExecutionBundle.message("run.on.targets.label.saved.targets")), SAVED_TARGETS_SECTION_INDEX);
    }
  }

  private void addAndSelectSavedTarget(@NotNull TargetEnvironmentConfiguration config) {
    addSavedTargetsSection();
    ((MyModel)getModel()).insertElementAt(new SavedTarget(config), SAVED_TARGETS_SECTION_INDEX + 1);
    setSelectedIndex(SAVED_TARGETS_SECTION_INDEX + 1);
  }

  public @Nullable String getSelectedTargetName() {
    return ObjectUtils.doIfCast(getSelectedItem(), Target.class, i -> i.getTargetName());
  }

  public void selectTarget(String configName) {
    if (configName == null) {
      setSelectedItem(null);
      return;
    }
    for (int i = 0; i < getModel().getSize(); i++) {
      Item at = getModel().getElementAt(i);
      if (at instanceof Target && configName.equals(((Target)at).getTargetName())) {
        setSelectedItem(at);
      }
    }
    //todo[remoteServers]: add invalid value
  }

  private void validateSelectedTarget() {
    Object selected = getSelectedItem();
    boolean hasErrors = false;
    if (selected instanceof SavedTarget target) {
      target.revalidateConfiguration();
      hasErrors = target.hasErrors();
    }
    putClientProperty("JComponent.outline", hasErrors ? "error" : null);
  }

  private final class MyModel extends DefaultComboBoxModel<Item> {
    @Override
    public void setSelectedItem(Object anObject) {
      if (anObject instanceof Type) {
        hidePopup();
        //noinspection unchecked,rawtypes
        TargetEnvironmentWizard wizard = ((Type)anObject).createWizard(myProject, myDefaultRuntimeType);
        if (wizard != null && wizard.showAndGet()) {
          TargetEnvironmentConfiguration newTarget = wizard.getSubject();
          TargetEnvironmentsManager.getInstance(myProject).addTarget(newTarget);
          addAndSelectSavedTarget(newTarget);
        }
        return;
      }
      super.setSelectedItem(anObject);
    }
  }
}
