// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr;

import com.intellij.ide.Prefs;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.BuildNumber;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class LicenseAiNonCommercialUtil {
  public static boolean isNonCommercialAiLicenseAvailable() {
    return "true".equals(System.getProperty("enable.non.commercial.ai.license"));
  }

  public static void storeAiNonCommercialTermsAcceptedState(@Nullable Boolean isAccepted) {
    if (isAccepted == null) {
      Prefs.remove(getAiTermsAcceptedKey());
      return;
    }
    Prefs.putBoolean(getAiTermsAcceptedKey(), isAccepted);
  }

  private static final @NotNull String AI_TERMS_ACCEPTED_PROPERTY_SUFFIX = "NonCommercialLicense.AiTermsAccepted";

  public static @NotNull String getAiTermsAcceptedKey() {
    return getPerApplicationKey(AI_TERMS_ACCEPTED_PROPERTY_SUFFIX);
  }

  private static @NotNull String getPerApplicationKey(@NotNull String keySuffix) {
    ApplicationInfoEx info = ApplicationInfoImpl.getShadowInstance();
    BuildNumber build = info.getBuild();
    return String.format("%s.%s.%d.%s", info.getShortCompanyName().replace(" ", ""), build.getProductCode(), build.getBaselineVersion(), keySuffix);
  }
}
