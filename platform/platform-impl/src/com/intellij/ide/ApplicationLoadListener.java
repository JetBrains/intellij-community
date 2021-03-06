// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;

@ApiStatus.Internal
public interface ApplicationLoadListener {
  ExtensionPointName<ApplicationLoadListener> EP_NAME = new ExtensionPointName<>("com.intellij.ApplicationLoadListener");

  /**
   * @deprecated Use {@link #beforeApplicationLoaded(Application, Path)}
   */
  @SuppressWarnings("unused")
  @Deprecated
  default void beforeApplicationLoaded(@NotNull Application application, @NotNull String configPath) {
  }

  default void beforeApplicationLoaded(@NotNull Application application, @NotNull Path configPath) {
    beforeApplicationLoaded(application, configPath.toString().replace(File.separatorChar, '/'));
  }
}
