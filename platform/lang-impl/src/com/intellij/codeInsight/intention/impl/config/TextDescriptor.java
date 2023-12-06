// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.intention.impl.config;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;


public interface TextDescriptor {
  @NotNull @Nls String getText() throws IOException;

  @NotNull @NonNls String getFileName();
}
