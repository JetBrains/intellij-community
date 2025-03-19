// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.Calendar;

/**
 * Provides product information.
 */
public abstract class ApplicationInfo {
  public static ApplicationInfo getInstance() {
    return ApplicationManager.getApplication().getService(ApplicationInfo.class);
  }

  public abstract Calendar getBuildDate();

  /**
   * Retrieves the Unix timestamp in seconds of when the build was created.
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  public abstract @NotNull ZonedDateTime getBuildTime();

  public abstract @NotNull BuildNumber getBuild();

  public abstract @NotNull String getApiVersion();

  public abstract String getMajorVersion();

  /**
   * The application's minor version string.
   * <p><b>Might include several numbers, do not try parsing this as an integer!</b></p>
   * <p><b>Example:</b> for IDE version 2024.2.1, <code>getMinorVersion()</code> might return <code>"2.1"</code> for some products.</p>
   *
   * @see #getMinorVersionMainPart()
   */
  public abstract String getMinorVersion();

  public abstract String getMicroVersion();

  public abstract String getPatchVersion();

  public abstract @NlsSafe String getVersionName();

  /**
   * Returns the first number from 'minor' part of the version.
   * This method is temporarily added because some products specify a composite number (like '1.3')
   * in 'minor version' attribute instead of using 'micro version' (i.e., set minor='1' micro='3').
   *
   * @see org.jetbrains.intellij.build.ApplicationInfoProperties#getMinorVersionMainPart
   */
  public final @NlsSafe String getMinorVersionMainPart() {
    String value = StringUtil.substringBefore(getMinorVersion(), ".");
    return value == null ? getMinorVersion() : value;
  }

  /**
   * Use this method to refer to the company in official contexts where it may have any legal implications.
   *
   * @return full name of the product vendor, e.g. 'JetBrains s.r.o.' for JetBrains products
   * @see #getShortCompanyName()
   */
  public abstract @NlsSafe String getCompanyName();

  /**
   * Use this method to refer to the company in a less formal way, e.g., in UI messages or directory names.
   *
   * @return shortened name of the product vendor without 'Inc.' or similar suffixes, e.g. 'JetBrains' for JetBrains products
   * @see #getCompanyName()
   */
  public abstract @NlsSafe String getShortCompanyName();

  public abstract String getCompanyURL();

  /**
   * @deprecated use properties from {@link com.intellij.platform.ide.customization.ExternalProductResourceUrls} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public abstract @Nullable String getProductUrl();

  /**
   * @deprecated use {@link com.intellij.platform.ide.customization.ExternalProductResourceUrls#getYouTubeChannelUrl()} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public abstract @Nullable String getJetBrainsTvUrl();

  public abstract boolean hasHelp();

  public abstract boolean hasContextHelp();

  public abstract @NlsSafe @NotNull String getFullVersion();

  /**
   * "major.minor"; when the minor version is composite, only the first part is used.
   */
  public final @NlsSafe String getShortVersion() {
    return getMajorVersion() + '.' + getMinorVersionMainPart();
  }

  public abstract @NlsSafe @NotNull String getStrictVersion();

  public static boolean helpAvailable() {
    if (!LoadingState.COMPONENTS_LOADED.isOccurred()) {
      return false;
    }
    ApplicationInfo info = getInstance();
    return info != null && info.hasHelp();
  }

  public static boolean contextHelpAvailable() {
    if (!LoadingState.COMPONENTS_LOADED.isOccurred()) {
      return false;
    }
    ApplicationInfo info = getInstance();
    return info != null && info.hasContextHelp();
  }

  public boolean isEAP() {
    return false;
  }

  public abstract String getFullApplicationName();

  public @Nullable String getSplashImageUrl() {
    return null;
  }

  /**
   * @return {@code true} if the specified plugin is an essential part of the IDE, so it cannot be disabled and isn't shown in <em>Settings | Plugins</em>.
   */
  @ApiStatus.Internal
  public abstract boolean isEssentialPlugin(@NotNull String pluginId);

  @ApiStatus.Internal
  public abstract boolean isEssentialPlugin(@NotNull PluginId pluginId);
}
