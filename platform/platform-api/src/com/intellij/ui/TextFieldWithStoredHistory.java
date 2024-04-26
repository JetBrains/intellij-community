// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

public class TextFieldWithStoredHistory extends TextFieldWithHistory {
  private final String myPropertyName;

  // API compatibility with 7.0.1
  public TextFieldWithStoredHistory(final @NonNls String propertyName, boolean cropList) {
    this(propertyName);
  }

  public TextFieldWithStoredHistory(final @NonNls String propertyName) {
    myPropertyName = propertyName;
    reset();
  }

  @Override
  public void addCurrentTextToHistory() {
    super.addCurrentTextToHistory();
    PropertiesComponent.getInstance().setValue(myPropertyName, StringUtil.join(getHistory(), "\n"));
  }

  public void reset() {
    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    final String history = propertiesComponent.getValue(myPropertyName);
    if (history != null) {
      final String[] items = history.split("\n");
      ArrayList<String> result = new ArrayList<>();
      for (String item : items) {
        if (item != null && !item.isEmpty()) {
          result.add(item);
        }
      }
      setHistory(result);
      setSelectedItem("");
    }
  }
}
