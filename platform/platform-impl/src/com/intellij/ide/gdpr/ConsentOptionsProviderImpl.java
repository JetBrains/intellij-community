// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr;

import com.intellij.ide.ConsentOptionsProvider;
import com.intellij.ide.gdpr.ui.consents.AiDataCollectionExternalSettings;
import com.intellij.ui.LicensingFacade;

import java.util.Set;

final class ConsentOptionsProviderImpl implements ConsentOptionsProvider {
  private static final Set<String> productsSupportingForcedConsent = Set.of("QA", "RR", "WS", "RD", "CL", "RM", "DB");

  private volatile long myLastModificationCount = -1;
  private volatile boolean mySendingAllowed = false;

  @Override
  public boolean isEAP() {
    return ConsentOptions.getInstance().isEAP();
  }

  @Override
  public boolean isActivatedWithFreeLicense() {
    // Using free non-commercial license is by EULA a consent to sending feature usage statistics for product improvements
    LicensingFacade facade = LicensingFacade.getInstance();
    if (facade == null || !productsSupportingForcedConsent.contains(facade.platformProductCode)) {
      return false;
    }
    String meta = facade.metadata;
    return meta != null && meta.length() > 10 && meta.charAt(10) == 'F';
  }

  @Override
  public void setSendingUsageStatsAllowed(boolean allowed) {
    ConsentOptions.getInstance().setSendingUsageStatsAllowed(allowed);
  }

  @Override
  public boolean isSendingUsageStatsAllowed() {
    long modificationCount = ConsentOptions.getInstance().getModificationCount();
    if (myLastModificationCount == modificationCount) {
      return mySendingAllowed;
    }

    boolean allowedNow = ConsentOptions.getInstance().isSendingUsageStatsAllowed() == ConsentOptions.Permission.YES;
    mySendingAllowed = allowedNow;
    myLastModificationCount = modificationCount;

    return allowedNow;
  }

  @Override
  public boolean isTraceDataCollectionAllowed() {
    DataCollectionAgreement dataCollectionAgreement = DataCollectionAgreement.getInstance();
    AiDataCollectionExternalSettings settings = AiDataCollectionExternalSettings.findSettingsImplementedByAiAssistant();
    boolean isAllowed = dataCollectionAgreement == DataCollectionAgreement.YES ||
                        ConsentOptions.getInstance().getTraceDataCollectionPermission() == ConsentOptions.Permission.YES;
    boolean isDisabled = settings != null && settings.isForciblyDisabled();
    return isAllowed && !isDisabled;
  }
}
