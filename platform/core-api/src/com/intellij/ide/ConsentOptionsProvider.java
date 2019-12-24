// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

public interface ConsentOptionsProvider {

  boolean isEAP();

  void setSendingUsageStatsAllowed(boolean allowed);

  boolean isSendingUsageStatsAllowed();
}
