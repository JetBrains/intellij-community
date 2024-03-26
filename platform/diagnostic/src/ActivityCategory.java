// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@Internal
// non-sequential and repeated items
public enum ActivityCategory {
  DEFAULT("item"),
  MAIN("item"),

  APP_COMPONENT("appComponents"),
  PROJECT_COMPONENT("projectComponents"),
  MODULE_COMPONENT("moduleComponents"),

  APP_SERVICE("appServices"),
  PROJECT_SERVICE("projectServices"),
  MODULE_SERVICE("moduleServices"),

  APP_EXTENSION("appExtensions"),
  PROJECT_EXTENSION("projectExtensions"),
  MODULE_EXTENSION("moduleExtensions"),

  PROJECT_OPEN_HANDLER("openHandler"),

  GC("GC"),

  SERVICE_WAITING("serviceWaiting")
  ;

  private final String jsonName;

  ActivityCategory(@NotNull String jsonName) {
    this.jsonName = jsonName;
  }

  public @NotNull String getJsonName() {
    return jsonName;
  }
}
