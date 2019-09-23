// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Calendar;

/**
 * Provides IDE version/help and vendor information.
 */
public abstract class ApplicationInfo {
  public abstract Calendar getBuildDate();

  public abstract BuildNumber getBuild();

  public abstract String getApiVersion();

  public abstract String getMajorVersion();

  public abstract String getMinorVersion();

  public abstract String getMicroVersion();

  public abstract String getPatchVersion();

  public abstract String getVersionName();

  /**
   * Returns the first number from 'minor' part of the version. This method is temporarily added because some products specify a composite number (like '1.3')
   * in 'minor version' attribute instead of using 'micro version' (i.e. set minor='1' micro='3').
   *
   * @see org.jetbrains.intellij.build.ApplicationInfoProperties#minorVersionMainPart
   */
  public final String getMinorVersionMainPart() {
    return ObjectUtils.notNull(StringUtil.substringBefore(getMinorVersion(), "."), getMinorVersion());
  }

  /**
   * Use this method to refer to the company in official contexts where it may have any legal implications.
   *
   * @return full name of the product vendor, e.g. 'JetBrains s.r.o.' for JetBrains products
   * @see #getShortCompanyName()
   */
  public abstract String getCompanyName();

  /**
   * Use this method to refer to the company in a less formal way, e.g. in UI messages or directory names.
   *
   * @return shortened name of the product vendor without 'Inc.' or similar suffixes, e.g. 'JetBrains' for JetBrains products
   * @see #getCompanyName()
   */
  public abstract String getShortCompanyName();

  public abstract String getCompanyURL();

  public abstract String getJetbrainsTvUrl();

  public abstract String getEvalLicenseUrl();

  public abstract String getKeyConversionUrl();

  @Nullable
  public abstract Rectangle getAboutLogoRect();

  public abstract boolean hasHelp();

  public abstract boolean hasContextHelp();

  public abstract String getFullVersion();

  public abstract String getStrictVersion();

  public static ApplicationInfo getInstance() {
    return ServiceManager.getService(ApplicationInfo.class);
  }

  public static boolean helpAvailable() {
    return ApplicationManager.getApplication() != null && getInstance() != null && getInstance().hasHelp();
  }

  public static boolean contextHelpAvailable() {
    return ApplicationManager.getApplication() != null && getInstance() != null && getInstance().hasContextHelp();
  }

  /** @deprecated use {@link #getBuild()} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public String getBuildNumber() {
    return getBuild().asString();
  }

  public abstract String getFullApplicationName();
}