// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion;

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Provides access to configuration of a component. It's important to do modification via this class to ensure that all changed files will be
 * included into the backup.
 */
public interface ComponentManagerSettings {
  @Nullable Element getComponentElement(@NotNull @NonNls String componentName);

  @NotNull Element getRootElement();

  @NotNull Path getPath();
}
