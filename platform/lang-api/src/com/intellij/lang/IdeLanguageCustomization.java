// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Override this service in your IDE to set which languages are considered as the primary languages. It isn't supposed to be overridden in plugins.
 */
@ApiStatus.Experimental
public class IdeLanguageCustomization {
  public static IdeLanguageCustomization getInstance() {
    return ServiceManager.getService(IdeLanguageCustomization.class);
  }

  /**
   * Returns the primary languages for which the IDE is supposed to be used. If there are several primary languages add them to the resulting list
   * in order of importance. This method is used to customize IDE's UI, e.g. to move settings pages related to a primary language to the top.
   */
  @NotNull
  public List<Language> getPrimaryIdeLanguages() {
    return Collections.emptyList();
  }
}
