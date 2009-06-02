package com.intellij.openapi.util.registry.ui;

import com.intellij.openapi.ui.CheckBoxWithDescription;
import com.intellij.openapi.util.registry.RegistryValue;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RegistryCheckBox extends CheckBoxWithDescription {

  private RegistryValue myValue;

  public RegistryCheckBox(RegistryValue value) {
    this(value, value.getDescription(), null);
  }

  public RegistryCheckBox(RegistryValue value, String text, @Nullable String longDescription) {
    super(new JCheckBox(text), longDescription);
    myValue = value;
    getCheckBox().setSelected(myValue.asBoolean());
  }

  public boolean isChanged() {
    return getCheckBox().isSelected() != myValue.asBoolean();
  }

  public void save() {
    myValue.setValue(Boolean.valueOf(getCheckBox().isSelected()).toString());
  }

}