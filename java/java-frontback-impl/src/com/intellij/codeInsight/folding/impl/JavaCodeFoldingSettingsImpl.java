// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "JavaCodeFoldingSettings", storages = @Storage("editor.xml"), category = SettingsCategory.CODE)
public final class JavaCodeFoldingSettingsImpl extends JavaCodeFoldingSettingsBase implements PersistentStateComponent<JavaCodeFoldingSettingsImpl> {
  @Override
  public JavaCodeFoldingSettingsImpl getState() {
    return this;
  }

  @Override
  public void loadState(final @NotNull JavaCodeFoldingSettingsImpl state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
