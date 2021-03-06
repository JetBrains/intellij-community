// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.target.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SeparatorWithText;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RunOnTargetComboBox extends ComboBox<RunOnTargetComboBox.Item> {
  public static final Logger LOGGER = Logger.getInstance(RunOnTargetComboBox.class);
  @NotNull private final Project myProject;
  @Nullable private LanguageRuntimeType<?> myDefaultRuntimeType;
  private boolean hasSavedTargets = false;

  public RunOnTargetComboBox(@NotNull Project project) {
    super();
    setModel(new MyModel());
    myProject = project;
    setRenderer(new MyRenderer());
    addActionListener(e -> validateSelectedTarget());
  }

  public void initModel() {
    hasSavedTargets = false;
    MyModel model = (MyModel)getModel();
    model.removeAllElements();
    model.addElement(null);

    Collection<Type<?>> types = new ArrayList<>();
    for (TargetEnvironmentType<?> type : TargetEnvironmentType.EXTENSION_NAME.getExtensionList()) {
      if (type.isSystemCompatible() && type.providesNewWizard(myProject, myDefaultRuntimeType)) {
        types.add(new Type<>(type));
      }
    }
    if (!types.isEmpty()) {
      model.addElement(new Separator(ExecutionBundle.message("run.on.targets.label.new.targets")));
      for (Type<?> type : types) {
        model.addElement(type);
      }
    }
  }

  public void setDefaultLanguageRuntimeType(@Nullable LanguageRuntimeType<?> defaultLanguageRuntimeType) {
    myDefaultRuntimeType = defaultLanguageRuntimeType;
  }

  @Nullable
  public LanguageRuntimeType<?> getDefaultLanguageRuntimeType() {
    return myDefaultRuntimeType;
  }

  public void addTarget(@NotNull TargetEnvironmentConfiguration config, int index) {
    if (!hasSavedTargets) {
      hasSavedTargets = true;
      ((MyModel)getModel()).insertElementAt(new Separator(ExecutionBundle.message("run.on.targets.label.saved.targets")), 1);
    }
    ((MyModel)getModel()).insertElementAt(new Target(config), index);
  }

  @Nullable
  public String getSelectedTargetName() {
    return ObjectUtils.doIfCast(getSelectedItem(), Item.class, i -> i.getDisplayName());
  }

  public void addTargets(List<? extends TargetEnvironmentConfiguration> configs) {
    int index = 2;
    for (TargetEnvironmentConfiguration config : configs) {
      addTarget(config, index);
      index++;
    }
  }

  public void selectTarget(String configName) {
    if (configName == null) {
      setSelectedItem(null);
      return;
    }
    for (int i = 0; i < getModel().getSize(); i++) {
      Item at = getModel().getElementAt(i);
      if (at instanceof Target && configName.equals(at.getDisplayName())) {
        setSelectedItem(at);
      }
    }
    //todo[remoteServers]: add invalid value
  }

  private void validateSelectedTarget() {
    Object selected = getSelectedItem();
    boolean hasErrors = false;
    if (selected instanceof Target) {
      Target target = (Target)selected;
      target.revalidateConfiguration();
      hasErrors = target.hasErrors();
    }
    putClientProperty("JComponent.outline", hasErrors ? "error" : null);
  }

  public static abstract class Item {
    private final @NlsContexts.Label String displayName;
    private final Icon icon;


    public Item(@NlsContexts.Label String displayName, Icon icon) {
      this.displayName = displayName;
      this.icon = icon;
    }

    public @NlsContexts.Label String getDisplayName() {
      return displayName;
    }

    public Icon getIcon() {
      return icon;
    }
  }

  private static final class Separator extends Item {
    private Separator(@NlsContexts.Label String displayName) {
      super(displayName, null);
    }
  }

  private static final class Target extends Item {
    private final TargetEnvironmentConfiguration myConfig;
    @Nullable private ValidationInfo myValidationInfo;

    private Target(TargetEnvironmentConfiguration config) {
      super(config.getDisplayName(), TargetEnvironmentConfigurationKt.getTargetType(config).getIcon());
      myConfig = config;
      revalidateConfiguration();
    }

    public void revalidateConfiguration() {
      try {
        myConfig.validateConfiguration();
        myValidationInfo = null;
      }
      catch (RuntimeConfigurationException e) {
        myValidationInfo = new ValidationInfo(e.getLocalizedMessage());
      }
    }

    @Nullable
    public ValidationInfo getValidationInfo() {
      return myValidationInfo;
    }

    public boolean hasErrors() {
      return getValidationInfo() != null;
    }

    @Override
    public Icon getIcon() {
      Icon rawIcon = super.getIcon();
      return rawIcon != null && hasErrors() ?
             LayeredIcon.create(rawIcon, AllIcons.RunConfigurations.InvalidConfigurationLayer) : rawIcon;
    }
  }

  private static final class Type<T extends TargetEnvironmentConfiguration> extends Item {
    @NotNull
    private final TargetEnvironmentType<T> type;

    private Type(@NotNull TargetEnvironmentType<T> type) {
      super(ExecutionBundle.message("run.on.targets.label.new.target.of.type", type.getDisplayName()), type.getIcon());
      this.type = type;
    }

    @Nullable
    TargetEnvironmentWizard createWizard(@NotNull Project project, @Nullable LanguageRuntimeType<?> languageRuntime) {
      return TargetEnvironmentWizard.createWizard(project, type, languageRuntime);
    }
  }

  private class MyModel extends DefaultComboBoxModel<RunOnTargetComboBox.Item> {
    @Override
    public void setSelectedItem(Object anObject) {
      if (anObject instanceof Separator) {
        return;
      }
      if (anObject instanceof Type) {
        hidePopup();
        //noinspection unchecked,rawtypes
        TargetEnvironmentWizard wizard = ((Type)anObject).createWizard(myProject, myDefaultRuntimeType);
        if (wizard != null && wizard.showAndGet()) {
          TargetEnvironmentConfiguration newTarget = wizard.getSubject();
          TargetEnvironmentsManager.getInstance(myProject).addTarget(newTarget);
          addTarget(newTarget, 2);
          setSelectedIndex(2);
        }
        return;
      }
      super.setSelectedItem(anObject);
    }
  }

  private static class MyRenderer extends ColoredListCellRenderer<RunOnTargetComboBox.Item> {
    @Override
    public Component getListCellRendererComponent(JList<? extends Item> list, Item value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof Separator) {
        SeparatorWithText separator = new SeparatorWithText();
        separator.setCaption(value.getDisplayName());
        return separator;
      }
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends Item> list, Item value, int index, boolean selected, boolean hasFocus) {
      if (value == null) {
        append(ExecutionBundle.message("local.machine"));
        setIcon(AllIcons.Nodes.HomeFolder);
      }
      else {
        append(value.getDisplayName());
        setIcon(value.getIcon());
      }
    }
  }
}
