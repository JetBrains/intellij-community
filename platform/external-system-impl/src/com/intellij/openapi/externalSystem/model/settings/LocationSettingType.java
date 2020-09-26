// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.settings;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import java.awt.*;

/**
 * Enumerates possible types of external project location setting.
 *
 * @author Denis Zhdanov
 */
public enum LocationSettingType {

  /** User hasn't defined location but the IDE discovered it automatically. */
  DEDUCED("setting.type.location.deduced", "TextField.inactiveForeground"),

  /** User hasn't defined location and the IDE was unable to discover it automatically. */
  UNKNOWN("setting.type.location.unknown"),

  /** User defined location but it's incorrect. */
  EXPLICIT_INCORRECT("setting.type.location.explicit.incorrect"),

  EXPLICIT_CORRECT("setting.type.location.explicit.correct");

  @PropertyKey(resourceBundle = ExternalSystemBundle.BUNDLE)
  @NotNull private final String myDescriptionKey;
  @NotNull private final Color myColor;

  LocationSettingType(@NotNull @PropertyKey(resourceBundle = ExternalSystemBundle.BUNDLE) String descriptionKey) {
    this(descriptionKey, "TextField.foreground");
  }

  LocationSettingType(@NotNull @PropertyKey(resourceBundle = ExternalSystemBundle.BUNDLE) String descriptionKey,
                      @NotNull String key)
  {
    myDescriptionKey = descriptionKey;
    myColor = JBColor.namedColor(key, UIManager.getColor(key));
  }

  /**
   * @return human-readable description of the current setting type
   */
  public String getDescription(@NotNull ProjectSystemId externalSystemId) {
    return ExternalSystemBundle.message(myDescriptionKey, externalSystemId.getReadableName());
  }

  @NotNull
  public Color getColor() {
    return myColor;
  }
}
