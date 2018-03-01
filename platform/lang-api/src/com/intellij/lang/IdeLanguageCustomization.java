// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.openapi.components.ServiceManager;

/**
 * Override this service in your IDE to set which language is considered as the main language. It isn't supposed to be overridden in plugins.
 */
public class IdeLanguageCustomization {
  public static IdeLanguageCustomization getInstance() {
    return ServiceManager.getService(IdeLanguageCustomization.class);
  }

  /**
   * Returns the main language for which the IDE is supposed to be used or {@code null} if there is no single main language. This method is
   * used to customize IDE's UI, e.g. to move settings pages related to the main language to the top.
   */
  public Language getMainIdeLanguage() {
    return null;
  }
}
