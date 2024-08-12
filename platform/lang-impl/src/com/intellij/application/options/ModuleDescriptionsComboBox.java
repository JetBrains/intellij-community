// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.icons.AllIcons;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.*;
import com.intellij.openapi.module.impl.LoadedModuleDescriptionImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.SortedComboBoxModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

/**
 * Combobox which may show not only regular loaded modules but also unloaded modules.
 * Use it instead of {@link ModulesComboBox} for configuration elements which may refer to unloaded modules.
 *
 * @see ModulesComboBox
 */
public final class ModuleDescriptionsComboBox extends ComboBox<ModuleDescription> implements ModulesCombo {
  private final SortedComboBoxModel<ModuleDescription> myModel;
  private boolean myAllowEmptySelection;

  public ModuleDescriptionsComboBox() {
    myModel = new SortedComboBoxModel<>(Comparator.comparing(description -> description != null ? description.getName() : "",
                                                             String.CASE_INSENSITIVE_ORDER));
    setModel(myModel);
    setSwingPopup(false);
    setRenderer(new ModuleDescriptionListCellRenderer());
  }

  @Override
  public void allowEmptySelection(@NotNull @NlsContexts.ListItem String emptySelectionText) {
    myAllowEmptySelection = true;
    myModel.add(null);
    setRenderer(new ModuleDescriptionListCellRenderer(emptySelectionText));
  }

  @Override
  public void setModules(@NotNull Collection<? extends Module> modules) {
    myModel.clear();
    for (Module module : modules) {
      myModel.add(new LoadedModuleDescriptionImpl(module));
    }
    if (myAllowEmptySelection) {
      myModel.add(null);
    }
  }

  public void setAllModulesFromProject(@NotNull Project project) {
    setModules(Arrays.asList(ModuleManager.getInstance(project).getModules()));
  }

  @Override
  public void setSelectedModule(@Nullable Module module) {
    myModel.setSelectedItem(module != null ? new LoadedModuleDescriptionImpl(module) : null);
  }

  @Override
  public void setSelectedModule(@NotNull Project project, @NotNull String moduleName) {
    Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
    if (module != null) {
      setSelectedModule(module);
    }
    else {
      UnloadedModuleDescription description = ModuleManager.getInstance(project).getUnloadedModuleDescription(moduleName);
      if (description != null) {
        if (myModel.indexOf(description) < 0) {
          myModel.add(description);
        }
        myModel.setSelectedItem(description);
      }
      else {
        myModel.setSelectedItem(null);
      }
    }
  }

  @Override
  public @Nullable Module getSelectedModule() {
    ModuleDescription selected = myModel.getSelectedItem();
    if (selected instanceof LoadedModuleDescription) {
      return ((LoadedModuleDescription)selected).getModule();
    }
    return null;
  }

  @Override
  public @Nullable String getSelectedModuleName() {
    ModuleDescription selected = myModel.getSelectedItem();
    return selected != null ? selected.getName() : null;
  }

  private static final class ModuleDescriptionListCellRenderer extends SimpleListCellRenderer<ModuleDescription> {
    private final @NlsContexts.ListItem String myEmptySelectionText;

    ModuleDescriptionListCellRenderer() {
      this(LangBundle.message("list.item.none"));
    }

    ModuleDescriptionListCellRenderer(@NotNull @NlsContexts.ListItem String emptySelectionText) {
      myEmptySelectionText = emptySelectionText;
    }

    @Override
    public void customize(@NotNull JList<? extends ModuleDescription> list, ModuleDescription value, int index, boolean selected, boolean hasFocus) {
      setText(value == null ? myEmptySelectionText : value.getName());
      setIcon(value instanceof LoadedModuleDescription
              ? ModuleType.get(((LoadedModuleDescription)value).getModule()).getIcon()
              : value != null
                ? AllIcons.Modules.UnloadedModule : null);
    }
  }
}
