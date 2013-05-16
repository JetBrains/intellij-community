package com.intellij.openapi.externalSystem.model.settings;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import java.awt.*;

/**
 * Enumerates possible types of 'gradle home' location setting.
 *
 * @author Denis Zhdanov
 * @since 9/2/11 3:58 PM
 */
public enum LocationSettingType {

  /** User hasn't defined gradle location but the IDE discovered it automatically. */
  DEDUCED("setting.type.location.deduced", "TextField.inactiveForeground"),

  /** User hasn't defined gradle location and the IDE was unable to discover it automatically. */
  UNKNOWN("setting.type.location.unknown"),

  /** User defined gradle location but it's incorrect. */
  EXPLICIT_INCORRECT("setting.type.location.explicit.correct"),

  EXPLICIT_CORRECT("setting.type.location.explicit.incorrect");

  @NotNull private final String myDescriptionKey;
  @NotNull private final String myColorKey;

  LocationSettingType(@NotNull String descriptionKey) {
    this(descriptionKey, "TextField.foreground");
  }

  LocationSettingType(@NotNull @PropertyKey(resourceBundle = ExternalSystemBundle.PATH_TO_BUNDLE) String descriptionKey,
                      @NotNull String colorKey)
  {
    myDescriptionKey = descriptionKey;
    myColorKey = colorKey;
  }

  /**
   * @return human-readable description of the current setting type
   */
  public String getDescription(@NotNull ProjectSystemId externalSystemId) {
    return ExternalSystemBundle.message(myDescriptionKey, externalSystemId.getReadableName());
  }

  @NotNull
  public Color getColor() {
    return UIManager.getColor(myColorKey);
  }
}
