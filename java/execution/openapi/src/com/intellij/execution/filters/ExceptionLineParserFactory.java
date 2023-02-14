// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public interface ExceptionLineParserFactory {
  ExceptionLineParser create(@NotNull ExceptionInfoCache cache);

  static ExceptionLineParserFactory getInstance() {
    return ApplicationManager.getApplication().getService(ExceptionLineParserFactory.class);
  }
}
