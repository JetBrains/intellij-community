// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper;

import externalApp.ExternalApp;
import org.jetbrains.annotations.NotNull;

public interface ScriptGenerator {
  @NotNull
  String commandLine(@NotNull Class<? extends ExternalApp> mainClass, boolean useBatchFile);
}
