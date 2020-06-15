// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

public interface ApplicationLoadListener {
  ExtensionPointName<ApplicationLoadListener> EP_NAME = new ExtensionPointName<>("com.intellij.ApplicationLoadListener");

  default void beforeApplicationLoaded(@NotNull Application application, @NotNull String configPath) {
  }
}
