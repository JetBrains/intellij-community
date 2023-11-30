// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor;

import com.intellij.json.JsonBundle;
import com.intellij.openapi.options.ConfigurableBuilder;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class JsonSmartKeysConfigurable extends ConfigurableBuilder implements SearchableConfigurable {
  public JsonSmartKeysConfigurable() {
    JsonEditorOptions settings = JsonEditorOptions.getInstance();
    if (settings == null) return;
    checkBox(JsonBundle.message("settings.smart.keys.insert.missing.comma.on.enter"),
             () -> settings.COMMA_ON_ENTER,
             v -> settings.COMMA_ON_ENTER = v);
    checkBox(JsonBundle.message("settings.smart.keys.insert.missing.comma.after.matching.braces.and.quotes"),
             () -> settings.COMMA_ON_MATCHING_BRACES,
             v -> settings.COMMA_ON_MATCHING_BRACES = v);
    checkBox(JsonBundle.message("settings.smart.keys.automatically.manage.commas.when.pasting.json.fragments"),
             () -> settings.COMMA_ON_PASTE,
             v -> settings.COMMA_ON_PASTE = v);
    checkBox(JsonBundle.message("settings.smart.keys.escape.text.on.paste.in.string.literals"),
             () -> settings.ESCAPE_PASTED_TEXT,
             v -> settings.ESCAPE_PASTED_TEXT = v);
    checkBox(JsonBundle.message("settings.smart.keys.automatically.add.quotes.to.property.names.when.typing.comma"),
             () -> settings.AUTO_QUOTE_PROP_NAME,
             v -> settings.AUTO_QUOTE_PROP_NAME = v);
    checkBox(JsonBundle.message("settings.smart.keys.automatically.add.whitespace.when.typing.comma.after.property.names"),
             () -> settings.AUTO_WHITESPACE_AFTER_COLON,
             v -> settings.AUTO_WHITESPACE_AFTER_COLON = v);
    checkBox(JsonBundle.message("settings.smart.keys.automatically.move.comma.after.the.property.name.if.typed.inside.quotes"),
             () -> settings.COLON_MOVE_OUTSIDE_QUOTES,
             v -> settings.COLON_MOVE_OUTSIDE_QUOTES = v);
    checkBox(JsonBundle.message("settings.smart.keys.automatically.move.comma.after.the.property.value.or.array.element.if.inside.quotes"),
             () -> settings.COMMA_MOVE_OUTSIDE_QUOTES,
             v -> settings.COMMA_MOVE_OUTSIDE_QUOTES = v);
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
    return JsonBundle.message("configurable.JsonSmartKeysConfigurable.display.name");
  }

  @Override
  public @NotNull String getId() {
    return "editor.preferences.jsonOptions";
  }
}
