// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class ModuleClasspathCombo extends JComboBox<ModuleClasspathCombo.Item> {

  private final Item[] myOptionItems;
  private boolean myPreventPopupClosing;

  public static class Item {
    public Item(Module module) {
      myModule = module;
    }

    public Item(String optionName) {
      myOptionName = optionName;
    }

    private Module myModule;
    private String myOptionName;
    public boolean myOptionValue;
  }

  public ModuleClasspathCombo(Item... optionItems) {
    myOptionItems = optionItems;
    setRenderer(new ListRenderer());
  }

  @Override
  public void setPopupVisible(boolean visible) {
    if (visible || !myPreventPopupClosing) {
      super.setPopupVisible(visible);
    }
    myPreventPopupClosing = false;
  }

  public CollectionComboBoxModel<Item> buildModel(Project project) {
    List<@NotNull Item> items = ContainerUtil
      .mapNotNull(ModuleManager.getInstance(project).getModules(), module -> isModuleAccepted(module) ? new Item(module) : null);
    CollectionComboBoxModel<Item> model = new ModelWithOptions();
    model.add(items);
    model.add(new Item((String)null));
    model.add(Arrays.asList(myOptionItems));
    setModel(model);
    return model;
  }

  public boolean isModuleAccepted(final Module module) {
    return ModuleTypeManager.getInstance().isClasspathProvider(ModuleType.get(module));
  }

  public void reset(ModuleBasedConfiguration configuration) {
    CollectionComboBoxModel<Item> model = buildModel(configuration.getProject());
    Module module = configuration.getConfigurationModule().getModule();
    setSelectedItem(ContainerUtil.find(model.getItems(), item -> module == item.myModule));
  }

  public void applyTo(ModuleBasedConfiguration configuration) {
    configuration.setModule(getSelectedModule());
  }

  @Nullable
  public Module getSelectedModule() {
    Item item = (Item)getSelectedItem();
    return item != null ? item.myModule : null;
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

  private static class ListRenderer extends ColoredListCellRenderer<Item> {
    private final JCheckBox myCheckBox = new JBCheckBox();
    @Override
    public Component getListCellRendererComponent(JList<? extends Item> list,
                                                  Item value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      if (value == null || value.myModule != null) {
        return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
      if (value.myOptionName == null) {
        JPanel pane = new JPanel(new GridBagLayout());
        pane.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        pane.add(new JSeparator(), gbc);
        return pane;
      }
      else {
        myCheckBox.setOpaque(false);
        myCheckBox.setText(value.myOptionName);
        myCheckBox.setSelected(value.myOptionValue);
        return myCheckBox;
      }
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends Item> list, Item value, int index, boolean selected, boolean hasFocus) {
      if (value == null) return;
      if (index == -1) {
        append("-cp ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        append(value.myModule.getName());
      }
      else {
        append(value.myModule.getName());
      }
    }
  }
}
