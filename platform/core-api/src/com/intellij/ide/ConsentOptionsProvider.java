// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import org.jetbrains.annotations.ApiStatus;

public interface ConsentOptionsProvider {

  boolean isEAP();

  boolean isActivatedWithFreeLicense();

  void setSendingUsageStatsAllowed(boolean allowed);

  boolean isSendingUsageStatsAllowed();

  @ApiStatus.Internal
  boolean isTraceDataCollectionAllowed();
}
