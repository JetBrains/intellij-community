// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.editor;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name = "JsonEditorOptions",
  storages = @Storage("editor.xml")
)
public class JsonEditorOptions implements PersistentStateComponent<JsonEditorOptions> {
  public boolean COMMA_ON_ENTER = true;
  public boolean COMMA_ON_MATCHING_BRACES = true;
  public boolean COMMA_ON_PASTE = true;
  public boolean AUTO_QUOTE_PROP_NAME = true;
  public boolean AUTO_WHITESPACE_AFTER_COLON = true;
  public boolean ESCAPE_PASTED_TEXT = true;
  public boolean COLON_MOVE_OUTSIDE_QUOTES = false;
  public boolean COMMA_MOVE_OUTSIDE_QUOTES = false;

  @Nullable
  @Override
  public JsonEditorOptions getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull JsonEditorOptions state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static JsonEditorOptions getInstance() {
    return ServiceManager.getService(JsonEditorOptions.class);
  }
}
