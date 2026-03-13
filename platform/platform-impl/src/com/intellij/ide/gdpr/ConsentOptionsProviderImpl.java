// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr;

import com.intellij.ide.ConsentOptionsProvider;
import com.intellij.ide.gdpr.localConsents.LocalConsentOptions;
import com.intellij.ide.gdpr.trace.TraceConsentManager;
import com.intellij.ide.gdpr.ui.consents.AiDataCollectionExternalSettings;
import com.intellij.ui.LicensingFacade;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

final class ConsentOptionsProviderImpl implements ConsentOptionsProvider {
  private static final Set<String> productsSupportingForcedConsent = Set.of("QA", "RR", "WS", "RD", "CL", "RM", "DB");
  private static final int METADATA_LICENSE_TYPE_INDEX = 10;

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
    return meta != null && meta.length() > METADATA_LICENSE_TYPE_INDEX && meta.charAt(METADATA_LICENSE_TYPE_INDEX) == 'F';
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
    LicensingFacade facade = LicensingFacade.getInstance();
    if (facade == null) {
      return false;
    }
    String metadata = facade.metadata;
    if (metadata == null) {
      return false;
    }
    AiDataCollectionExternalSettings settings = AiDataCollectionExternalSettings.findSettingsImplementedByAiAssistant();
    if (settings == null) {
      return false; // AIA plugin is required for TRACE data collection
    }
    boolean isAllowed = isTraceDataCollectionAllowedByMetadata(metadata);
    boolean isDisabled = settings.isForciblyDisabled();
    return isAllowed && !isDisabled;
  }

  private static boolean isTraceDataCollectionAllowedByMetadata(@NotNull String metadata) {
    if (metadata.length() <= METADATA_LICENSE_TYPE_INDEX) {
      return false;
    }
    TraceConsentManager traceConsentManager = TraceConsentManager.getInstance();
    if (traceConsentManager == null || !traceConsentManager.canDisplayTraceConsent()) {
      return false;
    }
    DataCollectionAgreement dataCollectionAgreement = DataCollectionAgreement.getInstance();
    ConsentOptions.Permission traceDataCollectionPermission = metadata.charAt(METADATA_LICENSE_TYPE_INDEX) == 'F'
                                                              ? LocalConsentOptions.INSTANCE.getTraceDataCollectionNonComPermission()
                                                              : LocalConsentOptions.INSTANCE.getTraceDataCollectionComPermission();
    return dataCollectionAgreement == DataCollectionAgreement.YES ||
           (dataCollectionAgreement != DataCollectionAgreement.NO &&
            traceDataCollectionPermission == ConsentOptions.Permission.YES);
  }
}
