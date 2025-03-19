// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name = "JsonEditorOptions",
  storages = @Storage("editor.xml"),
  category = SettingsCategory.CODE
)
public final class JsonEditorOptions implements PersistentStateComponent<JsonEditorOptions> {
  public boolean COMMA_ON_ENTER = true;
  public boolean COMMA_ON_MATCHING_BRACES = true;
  public boolean COMMA_ON_PASTE = true;
  public boolean AUTO_QUOTE_PROP_NAME = true;
  public boolean AUTO_WHITESPACE_AFTER_COLON = true;
  public boolean ESCAPE_PASTED_TEXT = true;
  public boolean COLON_MOVE_OUTSIDE_QUOTES = false;
  public boolean COMMA_MOVE_OUTSIDE_QUOTES = false;

  @Override
  public @Nullable JsonEditorOptions getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull JsonEditorOptions state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static JsonEditorOptions getInstance() {
    return ApplicationManager.getApplication().getService(JsonEditorOptions.class);
  }
}
