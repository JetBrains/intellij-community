// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion;

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public interface ComponentManagerSettings {
  @Nullable Element getComponentElement(@NotNull @NonNls String componentName);

  @NotNull Element getRootElement();

  @NotNull Path getPath();
}
