// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

/**
 * Override this service in your IDE to set which languages are considered as the primary languages. It isn't supposed to be overridden in plugins.
 */
public class IdeLanguageCustomization {
  public static IdeLanguageCustomization getInstance() {
    return ApplicationManager.getApplication().getService(IdeLanguageCustomization.class);
  }

  /**
   * Returns the primary languages for which the IDE is supposed to be used. If there are several primary languages add them to the resulting list
   * in order of importance. This method is used to customize IDE's UI, e.g. to move settings pages related to a primary language to the top.
   */
  public @NotNull @Unmodifiable List<Language> getPrimaryIdeLanguages() {
    return Collections.emptyList();
  }
}
