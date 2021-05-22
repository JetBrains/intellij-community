// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.application.options.ModulesCombo;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class ModuleClasspathCombo extends ComboBox<ModuleClasspathCombo.Item> implements ModulesCombo {

  private final Item[] myOptionItems;
  private boolean myPreventPopupClosing;
  private @Nullable @Nls String myNoModule;
  private static final Item mySeparator = new Item((String)null);

  public static class Item {
    public Item(Module module) {
      myModule = module;
    }

    public Item(String optionName) {
      myOptionName = optionName;
    }

    private Module myModule;
    private @NlsSafe String myOptionName;
    public boolean myOptionValue;
  }

  public ModuleClasspathCombo(Item... optionItems) {
    myOptionItems = optionItems;
    setRenderer(new ListRenderer());
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
    List<@NotNull Item> items = ContainerUtil.map(modules, Item::new);
    items.sort(Comparator.comparing(o -> o.myModule.getName()));
    CollectionComboBoxModel<Item> model = new ModelWithOptions();
    model.add(items);
    if (myNoModule != null) {
      model.add((Item)null);
    }
    if (myOptionItems.length > 0) {
      model.add(mySeparator);
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
  @Nullable
  public Module getSelectedModule() {
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

  private class ListRenderer extends ColoredListCellRenderer<Item> {
    private final JCheckBox myCheckBox = new JBCheckBox();
    @Override
    public Component getListCellRendererComponent(JList<? extends Item> list,
                                                  Item value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      if (value == mySeparator) {
        JPanel pane = new JPanel(new GridBagLayout());
        pane.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        pane.add(new JSeparator(), gbc);
        return pane;
      }
      else if (value != null && value.myOptionName != null){
        myCheckBox.setOpaque(false);
        myCheckBox.setText(value.myOptionName);
        myCheckBox.setSelected(value.myOptionValue);
        return myCheckBox;
      }
      return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends Item> list, Item value, int index, boolean selected, boolean hasFocus) {
      String name = value == null || value.myModule == null ? myNoModule : value.myModule.getName();
      if (index == -1 && name != null) {
        //noinspection HardCodedStringLiteral
        append("-cp ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      append(StringUtil.notNullize(name));
    }
  }
}
