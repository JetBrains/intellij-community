// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import org.jetbrains.annotations.NotNull;

// non-sequential and repeated items
public enum ActivityCategory {
  DEFAULT("item"),
  MAIN("item"),
  APP_OPTIONS_TOP_HIT_PROVIDER("appOptionsTopHitProvider"), PROJECT_OPTIONS_TOP_HIT_PROVIDER("projectOptionsTopHitProvider"),

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

  POST_STARTUP_ACTIVITY("projectPostStartupActivity"),
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
