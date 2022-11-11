// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr;

import com.intellij.ide.ConsentOptionsProvider;

final class ConsentOptionsProviderImpl implements ConsentOptionsProvider {
  @Override
  public boolean isEAP() {
    return ConsentOptions.getInstance().isEAP();
  }

  @Override
  public void setSendingUsageStatsAllowed(boolean allowed) {
    ConsentOptions.getInstance().setSendingUsageStatsAllowed(allowed);
  }

  @Override
  public boolean isSendingUsageStatsAllowed() {
    return ConsentOptions.getInstance().isSendingUsageStatsAllowed() == ConsentOptions.Permission.YES;
  }
}
