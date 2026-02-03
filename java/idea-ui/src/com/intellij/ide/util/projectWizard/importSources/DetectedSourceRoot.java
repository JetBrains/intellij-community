// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard.importSources;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class DetectedSourceRoot extends DetectedProjectRoot {
  private final String myPackagePrefix;

  public DetectedSourceRoot(final File directory, @Nullable String packagePrefix) {
    super(directory);
    myPackagePrefix = packagePrefix;
  }

  public @NotNull @NlsSafe String getPackagePrefix() {
    return StringUtil.notNullize(myPackagePrefix);
  }
}
