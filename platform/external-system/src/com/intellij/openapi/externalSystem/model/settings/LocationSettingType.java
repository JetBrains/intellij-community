package com.intellij.openapi.externalSystem.model.settings;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
<<<<<<< HEAD
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
=======
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * Enumerates possible types of 'gradle home' location setting.
 * 
 * @author Denis Zhdanov
 * @since 9/2/11 3:58 PM
 */
public enum LocationSettingType {
  
  /** User hasn't defined gradle location but the IDE discovered it automatically. */
  DEDUCED("setting.type.location.deduced"),
  
  /** User hasn't defined gradle location and the IDE was unable to discover it automatically. */
  UNKNOWN("setting.type.location.unknown"),
  
  /** User defined gradle location but it's incorrect. */
  EXPLICIT_INCORRECT("setting.type.location.explicit.correct"),
  
  EXPLICIT_CORRECT("setting.type.location.explicit.incorrect");
  
  private final String myKey;

  LocationSettingType(@NotNull @PropertyKey(resourceBundle = ExternalSystemBundle.PATH_TO_BUNDLE) String key) {
    myKey = key;
  }

  /**
   * @return    human-readable description of the current setting type
   */
  public String getDescription(@NotNull ProjectSystemId externalSystemId) {
<<<<<<< HEAD
    return ExternalSystemBundle.message(myKey, ExternalSystemUtil.toReadableName(externalSystemId));
=======
    String systemName = StringUtil.capitalize(externalSystemId.toString().toLowerCase());
    return ExternalSystemBundle.message(myKey, systemName);
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
  }
}
