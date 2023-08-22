// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.registry.RegistryManager;

import javax.swing.*;

public final class DistractionFreeModeController {
  static final String KEY = "editor.distraction.free.mode";
  static final String BEFORE = "BEFORE.DISTRACTION.MODE.";
  static final String AFTER = "AFTER.DISTRACTION.MODE.";
  static final String LAST_ENTER_VALUE = "DISTRACTION.MODE.ENTER.VALUE";

  public static boolean shouldMinimizeCustomHeader() {
    return PropertiesComponent.getInstance().getBoolean(LAST_ENTER_VALUE, false);
  }

  public static int getStandardTabPlacement() {
    if (!isDistractionFreeModeEnabled()) {
      return UISettings.getInstance().getEditorTabPlacement();
    }
    return PropertiesComponent.getInstance().getInt(BEFORE + "EDITOR_TAB_PLACEMENT", SwingConstants.TOP);
  }

  public static boolean isDistractionFreeModeEnabled() {
    return RegistryManager.getInstance().is(KEY);
  }
}
