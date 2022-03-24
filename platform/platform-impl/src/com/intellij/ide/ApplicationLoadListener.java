// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Third-party plugins must not use this extension.
 */
@ApiStatus.Internal
public interface ApplicationLoadListener {
  ExtensionPointName<ApplicationLoadListener> EP_NAME = new ExtensionPointName<>("com.intellij.ApplicationLoadListener");

  void beforeApplicationLoaded(@NotNull Application application, @NotNull Path configPath);
}
