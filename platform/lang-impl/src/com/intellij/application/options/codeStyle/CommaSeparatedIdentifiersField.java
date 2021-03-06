// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.ui.components.fields.valueEditors.TextFieldValueEditor;
import com.intellij.ui.components.fields.valueEditors.ValueEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CommaSeparatedIdentifiersField extends ExpandableTextField {

  private final @NotNull MyValueEditor myValueEditor;
  private String myValueName;

  public CommaSeparatedIdentifiersField() {
    myValueEditor = new MyValueEditor(this);
    setToolTipText(ApplicationBundle.message("settings.code.style.builder.methods.tooltip"));
  }

  @NotNull
  public ValueEditor<String> getEditor() {
    return myValueEditor;
  }

  public void setValueName(String valueName) {
    myValueName = valueName;
  }

  private class MyValueEditor extends TextFieldValueEditor<String> {

    private MyValueEditor(@NotNull JTextField field) {
      super(field, myValueName, "");
    }

    @NotNull
    @Override
    public String parseValue(@Nullable String text) throws InvalidDataException {
      if (text == null) return "";
      StringBuilder result = new StringBuilder();
      for(String chunk : StringUtil.split(text, ",")) {
        String identifier = chunk.trim();
        if (!StringUtil.isEmpty(identifier)) {
          if (StringUtil.isJavaIdentifier(identifier)) {
            if (result.length() > 0) {
              result.append(',');
            }
            result.append(identifier);
          }
          else {
            throw new InvalidDataException("Identifier required");
          }
        }
      }
      return result.toString();
    }

    @Override
    public String valueToString(@NotNull String value) {
      return value;
    }

    @Override
    public boolean isValid(@NotNull String value) {
      for(String chunk : StringUtil.split(value, ",")) {
        String identifier = chunk.trim();
        if (!StringUtil.isEmpty(identifier) && !StringUtil.isJavaIdentifier(identifier)) return false;
      }
      return true;
    }
  }
}
