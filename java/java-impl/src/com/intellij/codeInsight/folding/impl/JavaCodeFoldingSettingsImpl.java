// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "JavaCodeFoldingSettings", storages = @Storage("editor.xml"))
public class JavaCodeFoldingSettingsImpl extends JavaCodeFoldingSettingsBase implements PersistentStateComponent<JavaCodeFoldingSettingsImpl> {
  @Override
  public JavaCodeFoldingSettingsImpl getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull final JavaCodeFoldingSettingsImpl state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
