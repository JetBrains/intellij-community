// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.folding;

import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "CodeFoldingSettings", storages = @Storage("editor.xml"), category = SettingsCategory.CODE)
final class CodeFoldingSettingsImpl extends CodeFoldingSettings implements PersistentStateComponent<CodeFoldingSettings> {
  @Override
  public CodeFoldingSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull CodeFoldingSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public void noStateLoaded() {
    loadState(new CodeFoldingSettings());
  }
}
