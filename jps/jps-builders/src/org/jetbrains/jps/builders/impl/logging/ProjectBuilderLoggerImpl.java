// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.impl.logging;

import com.intellij.openapi.diagnostic.Logger;

public final class ProjectBuilderLoggerImpl extends ProjectBuilderLoggerBase {
  private static final Logger LOG = Logger.getInstance(ProjectBuilderLoggerImpl.class);

  @Override
  public boolean isEnabled() {
    return LOG.isDebugEnabled();
  }

  @Override
  protected void logLine(final String message) {
    LOG.debug(message);
  }
}
