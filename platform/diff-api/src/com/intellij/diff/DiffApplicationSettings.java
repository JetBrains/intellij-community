// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(
  name = "DiffApplicationSettings",
  storages = @Storage("vcs.xml")
)
public class DiffApplicationSettings implements PersistentStateComponent<DiffApplicationSettings> {

  public boolean SHOW_LST_WORD_DIFFERENCES = true;

  public static DiffApplicationSettings getInstance() {
    return ServiceManager.getService(DiffApplicationSettings.class);
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
