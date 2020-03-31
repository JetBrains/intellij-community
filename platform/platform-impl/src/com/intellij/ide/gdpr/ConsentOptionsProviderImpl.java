// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.gdpr;

import com.intellij.ide.ConsentOptionsProvider;

public class ConsentOptionsProviderImpl implements ConsentOptionsProvider {
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
