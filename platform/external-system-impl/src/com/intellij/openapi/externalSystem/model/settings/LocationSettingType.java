package com.intellij.openapi.externalSystem.model.settings;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * Enumerates possible types of 'gradle home' location setting.
 *
 * @author Denis Zhdanov
 * @since 9/2/11 3:58 PM
 */
public enum LocationSettingType {

  /** User hasn't defined gradle location but the IDE discovered it automatically. */
  DEDUCED("setting.type.location.deduced", "TextField.inactiveForeground", "nimbusDisabledText"),

  /** User hasn't defined gradle location and the IDE was unable to discover it automatically. */
  UNKNOWN("setting.type.location.unknown"),

  /** User defined gradle location but it's incorrect. */
  EXPLICIT_INCORRECT("setting.type.location.explicit.incorrect"),

  EXPLICIT_CORRECT("setting.type.location.explicit.correct");
  
  @NotNull private final String myDescriptionKey;
  @NotNull private final Color myColor;

  LocationSettingType(@NotNull String descriptionKey) {
    this(descriptionKey, "TextField.foreground");
  }

  LocationSettingType(@NotNull @PropertyKey(resourceBundle = ExternalSystemBundle.PATH_TO_BUNDLE) String descriptionKey,
                      @NotNull String ... colorKeys)
  {
    myDescriptionKey = descriptionKey;
    Color c = null;
    for (String key : colorKeys) {
      c = UIManager.getColor(key);
      if (c != null) {
        break;
      }
    }
    
    assert c != null : "Can't find color for keys " + Arrays.toString(colorKeys);
    myColor = c;
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
