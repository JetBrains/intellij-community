// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.nio.file.Path;

@ApiStatus.Internal
public interface EditMemorySettingsService {
  static EditMemorySettingsService getInstance() {
    return ApplicationManager.getApplication().getService(EditMemorySettingsService.class);
  }

  Path getUserOptionsFile();

  void save(VMOptions.MemoryKind option, int value) throws IOException;
}
