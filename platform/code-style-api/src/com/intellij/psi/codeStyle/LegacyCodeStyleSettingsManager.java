// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * Supports old code style settings (before version 2017.3). The settings are returned as is without any
 * changes.
 */
@Service(Service.Level.PROJECT)
@State(name = "ProjectCodeStyleSettingsManager", storages = @Storage("codeStyleSettings.xml"))
public final class LegacyCodeStyleSettingsManager implements PersistentStateComponent<Element> {
  private Element myState;
  
  @Override
  public Element getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull Element state) {
    myState = state;
  }
}
