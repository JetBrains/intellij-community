// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.jps.incremental.GlobalContextKey;

@ApiStatus.Internal
public final class ExternalJavacManagerKey {
  private ExternalJavacManagerKey() {}

  // avoid loading ExternalJavacManager class and Netty lib (IncProjectBuilder.flushContext checks this key)
  public static final GlobalContextKey<ExternalJavacManager> KEY = GlobalContextKey.create("_external_javac_server_");
}
