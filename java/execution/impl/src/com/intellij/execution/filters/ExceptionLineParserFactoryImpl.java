// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import org.jetbrains.annotations.NotNull;

public final class ExceptionLineParserFactoryImpl implements ExceptionLineParserFactory {
  @Override
  public ExceptionLineParser create(@NotNull ExceptionInfoCache cache) {
    return new ExceptionLineParserImpl(cache);
  }
}
