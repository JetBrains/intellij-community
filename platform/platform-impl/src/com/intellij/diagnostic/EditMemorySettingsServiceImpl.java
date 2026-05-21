// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

@ApiStatus.Internal
public class EditMemorySettingsServiceImpl implements EditMemorySettingsService {
  @Override
  public @Nullable Path getUserOptionsFile() {
    return VMOptions.getUserOptionsFile();
  }

  @Override
  public void save(VMOptions.@NotNull MemoryKind option, int value) throws IOException {
    VMOptions.setOption(option, value);
  }
}
