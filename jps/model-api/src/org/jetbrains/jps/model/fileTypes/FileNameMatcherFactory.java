// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.fileTypes;

import com.intellij.openapi.fileTypes.FileNameMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.service.JpsServiceManager;

public abstract class FileNameMatcherFactory {
  public static FileNameMatcherFactory getInstance() {
    return JpsServiceManager.getInstance().getService(FileNameMatcherFactory.class);
  }

  public abstract @NotNull FileNameMatcher createMatcher(@NotNull String pattern);
}
