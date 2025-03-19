// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.registry.RegistryManager;

import javax.swing.*;

public final class DistractionFreeModeController {
  static final String KEY = "editor.distraction.free.mode";
  public static final String DISTRACTION_MODE_PROPERTY_KEY = "DISTRACTION.MODE.";
  static final String BEFORE = "BEFORE." + DISTRACTION_MODE_PROPERTY_KEY;
  static final String AFTER = "AFTER." + DISTRACTION_MODE_PROPERTY_KEY;
  static final String LAST_ENTER_VALUE = DISTRACTION_MODE_PROPERTY_KEY + "ENTER.VALUE";

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
