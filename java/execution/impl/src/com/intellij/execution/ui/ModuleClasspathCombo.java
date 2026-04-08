// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.application.options.ModulesCombo;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class ModuleClasspathCombo extends ComboBox<ModuleClasspathCombo.Item> implements ModulesCombo {

  private final Item[] myOptionItems;
  private boolean myPreventPopupClosing;
  private @Nullable @Nls String myNoModule;

  public static class Item {

    private Item(Module module, @Nls String optionName) {
      myModule = module;
      myOptionName = optionName;
    }

    public Item(Module module) {
      this(module, null);
    }

    public Item(@Nls String optionName) {
      this(null, optionName);
    }

    private final Module myModule;
    private final @Nls String myOptionName;
    public boolean myOptionValue;

    @ApiStatus.Internal
    public Module getModule() {
      return myModule;
    }

    @ApiStatus.Internal
    public @Nls String getOptionName() {
      return myOptionName;
    }
  }

  public ModuleClasspathCombo(Item... optionItems) {
    myOptionItems = optionItems;
    setRenderer(ModuleClasspathRendererKt.createRenderer(ArrayUtil.getFirstElement(optionItems), () -> myNoModule));
    setSwingPopup(false);
    ComboboxSpeedSearch.installSpeedSearch(this, item -> item.myModule == null ? "" : item.myModule.getName());
  }

  @Override
  public void setPopupVisible(boolean visible) {
    if (visible || !myPreventPopupClosing) {
      super.setPopupVisible(visible);
    }
    myPreventPopupClosing = false;
  }

  private void buildModel(@NotNull Collection<? extends Module> modules) {
    List<@NotNull Item> items = ContainerUtil.sorted(ContainerUtil.map(modules, Item::new),
    Comparator.comparing(o -> o.myModule.getName()));
    CollectionComboBoxModel<Item> model = new ModelWithOptions();
    model.add(items);
    if (myNoModule != null) {
      model.add((Item)null);
    }
    model.add(Arrays.asList(myOptionItems));
    setModel(model);
  }

  private static boolean isModuleAccepted(final Module module) {
    return ModuleTypeManager.getInstance().isClasspathProvider(ModuleType.get(module));
  }

  public void reset(ModuleBasedConfiguration<?,?> configuration) {
    Module[] all = ModuleManager.getInstance(configuration.getProject()).getModules();
    buildModel(ContainerUtil.filter(all, ModuleClasspathCombo::isModuleAccepted));
    setSelectedModule(configuration.getConfigurationModule().getModule());
  }

  public void applyTo(ModuleBasedConfiguration<?,?> configuration) {
    configuration.setModule(getSelectedModule());
  }

  @Override
  public @Nullable Module getSelectedModule() {
    Item item = (Item)getSelectedItem();
    return item != null ? item.myModule : null;
  }

  @Override
  public void setSelectedModule(Module module) {
    List<Item> items = ((CollectionComboBoxModel<Item>)super.getModel()).getItems();
    setSelectedItem(ContainerUtil.find(items, item -> item != null && module == item.myModule));
  }

  @Override
  public void setModules(@NotNull Collection<? extends Module> modules) {
    buildModel(modules);
  }

  @Override
  public void allowEmptySelection(@NotNull String noModuleText) {
    myNoModule = noModuleText;
  }

  @Override
  public String getSelectedModuleName() {
    Module module = getSelectedModule();
    return module == null ? null : module.getName();
  }

  @Override
  public void setSelectedModule(@NotNull Project project, @NotNull String name) {
    List<Item> items = ((CollectionComboBoxModel<Item>)super.getModel()).getItems();
    Item selectedItem = ContainerUtil.find(items, item -> item.myModule != null && item.myModule.getName().equals(name));
    setSelectedItem(selectedItem);
  }

  private class ModelWithOptions extends CollectionComboBoxModel<Item> {
    @Override
    public void setSelectedItem(@Nullable Object o) {
      Item item = (Item)o;
      if (item == null || item.myModule != null) {
        myPreventPopupClosing = false;
        super.setSelectedItem(item);
      }
      else {
        item.myOptionValue = !item.myOptionValue;
        myPreventPopupClosing = true;
        update();
      }
    }
  }
}
