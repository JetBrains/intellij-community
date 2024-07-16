// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@State(
  name = "DiffApplicationSettings",
  storages = @Storage("vcs.xml"),
  category = SettingsCategory.TOOLS
)
@ApiStatus.Internal
public class DiffApplicationSettings implements PersistentStateComponent<DiffApplicationSettings> {

  public boolean SHOW_LST_WORD_DIFFERENCES = true;

  public static DiffApplicationSettings getInstance() {
    return ApplicationManager.getApplication().getService(DiffApplicationSettings.class);
  }

  @Override
  public DiffApplicationSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull DiffApplicationSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
