// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * This message bundle contains strings which somehow mention 'project' concept. Other IDEs may use a different term for that (e.g. Rider
 * use 'solution'). Don't use this class directly, use {@link IdeUICustomization#projectMessage} instead.
 */
final class ProjectConceptBundle extends DynamicBundle {
  @NonNls public static final String BUNDLE = "messages.ProjectConceptBundle";
  private static final ProjectConceptBundle INSTANCE = new ProjectConceptBundle();

  private ProjectConceptBundle() {
    super(BUNDLE);
  }

  @NotNull
  static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }
}
