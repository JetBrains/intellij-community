// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.hosting;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a remote service that hosts VCS repositories, e.g GitHub, BitBucket
 */
public interface RepositoryHostingService {
  /**
   * @return service name that will be used in UI actions
   */
  @NotNull
  String getServiceDisplayName();
}
