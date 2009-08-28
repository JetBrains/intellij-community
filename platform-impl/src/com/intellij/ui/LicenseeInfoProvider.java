package com.intellij.ui;

import com.intellij.openapi.components.ServiceManager;

/**
 * @author yole
 */
public abstract class LicenseeInfoProvider {
  public static LicenseeInfoProvider getInstance() {
    return ServiceManager.getService(LicenseeInfoProvider.class);
  }

  public abstract String getLicensedToMessage();
  public abstract String getLicenseRestrictionsMessage();
}
