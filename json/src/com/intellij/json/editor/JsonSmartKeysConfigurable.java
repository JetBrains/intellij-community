// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.editor;

import com.intellij.openapi.options.BeanConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;

public class JsonSmartKeysConfigurable extends BeanConfigurable<JsonEditorOptions> implements UnnamedConfigurable {
  public JsonSmartKeysConfigurable() {
    super(JsonEditorOptions.getInstance());
    JsonEditorOptions settings = getInstance();

    checkBox("Insert missing comma on enter",
             () -> settings.COMMA_ON_ENTER,
             v -> settings.COMMA_ON_ENTER = v);
    checkBox("Insert missing comma after matching braces and quotes",
             () -> settings.COMMA_ON_MATCHING_BRACES,
             v -> settings.COMMA_ON_MATCHING_BRACES = v);
    checkBox("Escape text on paste in string literals",
             () -> settings.ESCAPE_PASTED_TEXT,
             v -> settings.ESCAPE_PASTED_TEXT = v);
  }

  @Override
  public JComponent createComponent() {
    JComponent result = super.createComponent();
    assert result != null;
    result.setBorder(IdeBorderFactory.createTitledBorder("JSON"));
    return result;
  }
}
