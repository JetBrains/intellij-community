// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.editor;

import com.intellij.openapi.options.BeanConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class JsonSmartKeysConfigurable extends BeanConfigurable<JsonEditorOptions> implements SearchableConfigurable {
  public JsonSmartKeysConfigurable() {
    super(JsonEditorOptions.getInstance());
    JsonEditorOptions settings = getInstance();
    if (settings == null) return;
    checkBox("Insert missing comma on Enter",
             () -> settings.COMMA_ON_ENTER,
             v -> settings.COMMA_ON_ENTER = v);
    checkBox("Insert missing comma after matching braces and quotes",
             () -> settings.COMMA_ON_MATCHING_BRACES,
             v -> settings.COMMA_ON_MATCHING_BRACES = v);
    checkBox("Automatically manage commas when pasting JSON fragments",
             () -> settings.COMMA_ON_PASTE,
             v -> settings.COMMA_ON_PASTE = v);
    checkBox("Escape text on paste in string literals",
             () -> settings.ESCAPE_PASTED_TEXT,
             v -> settings.ESCAPE_PASTED_TEXT = v);
    checkBox("Automatically add quotes to property names when typing ':'",
             () -> settings.AUTO_QUOTE_PROP_NAME,
             v -> settings.AUTO_QUOTE_PROP_NAME = v);
    checkBox("Automatically add whitespace when typing ':' after property names",
             () -> settings.AUTO_WHITESPACE_AFTER_COLON,
             v -> settings.AUTO_WHITESPACE_AFTER_COLON = v);
    checkBox("Automatically move ':' after the property name if typed inside quotes",
             () -> settings.COLON_MOVE_OUTSIDE_QUOTES,
             v -> settings.COLON_MOVE_OUTSIDE_QUOTES = v);
    checkBox("Automatically move comma after the property value or array element if inside quotes",
             () -> settings.COMMA_MOVE_OUTSIDE_QUOTES,
             v -> settings.COMMA_MOVE_OUTSIDE_QUOTES = v);
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  @Override
  public String getDisplayName() {
    return "JSON";
  }

  @NotNull
  @Override
  public String getId() {
    return "editor.preferences.jsonOptions";
  }
}
