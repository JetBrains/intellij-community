package com.intellij.openapi.externalSystem.util;

import com.intellij.AbstractBundle;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author Denis Zhdanov
 * @since 8/1/11 2:44 PM
 */
public class ExternalSystemBundle extends AbstractBundle {

  public static final String PATH_TO_BUNDLE = "i18n.ExternalSystemBundle";

  private static final ExternalSystemBundle BUNDLE = new ExternalSystemBundle();

  public ExternalSystemBundle() {
    super(PATH_TO_BUNDLE);
  }

  public static String message(@PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object... params) {
    return BUNDLE.getMessage(key, params);
  }
}
