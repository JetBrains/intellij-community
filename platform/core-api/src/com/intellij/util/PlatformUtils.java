// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.application.ApplicationInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * This class allows changing behavior of the platform and plugins in specific IDEs. But if its methods are used for something it means that third-party
 * IDEs not listed here won't be able to get the desired behavior. Also, it's hard to correctly select IDEs where customizations should be
 * enabled, and there is no chance that such code will be properly updated when new IDEs or their editions appear.
 * So <strong>it's strongly not recommended to use methods from this class</strong>.
 * <p>
 * If you need to customize behavior of the platform somewhere, you should create a special application service for that and override it in
 * a specific IDE (look at {@link com.intellij.lang.IdeLanguageCustomization} and {@link com.intellij.openapi.updateSettings.UpdateStrategyCustomization}
 * for example).
 * </p>
 * <p>
 * If you need to customize behavior of a plugin depending on the IDE it's installed, it's better to use optional dependency on a corresponding
 * plugin or IDE module. See <a href="https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html#modules">SDK Docs</a>.
 * </p>
 * @author Konstantin Bulenkov, Nikolay Chashnikov
 */
@ApiStatus.Internal
public final class PlatformUtils {
  public static final String PLATFORM_PREFIX_KEY = "idea.platform.prefix";

  // NOTE: If you add any new prefixes to this list, please update the IntelliJPlatformProduct class in DevKit plugin
  public static final String IDEA_PREFIX = "idea";
  public static final String IDEA_CE_PREFIX = "Idea";
  public static final String IDEA_EDU_PREFIX = "IdeaEdu";
  public static final String APPCODE_PREFIX = "AppCode";
  public static final String AQUA_PREFIX = "Aqua";
  public static final String CLION_PREFIX = "CLion";
  public static final String PYCHARM_PREFIX = "Python";
  public static final String PYCHARM_CE_PREFIX = "PyCharmCore";
  public static final String DATASPELL_PREFIX = "DataSpell";
  public static final String PYCHARM_EDU_PREFIX = "PyCharmEdu";
  public static final String RUBY_PREFIX = "Ruby";
  public static final String PHP_PREFIX = "PhpStorm";
  public static final String WEB_PREFIX = "WebStorm";
  public static final String DBE_PREFIX = "DataGrip";
  public static final String RIDER_PREFIX = "Rider";
  public static final String GOIDE_PREFIX = "GoLand";
  public static final String FLEET_PREFIX = "FleetBackend";
  public static final String RUSTROVER_PREFIX = "RustRover";
  public static final String WRITERSIDE_PREFIX = "Writerside";
  public static final String GIT_CLIENT_PREFIX = "GitClient";
  public static final String MPS_PREFIX = "MPS";

  /**
   * @deprecated Code With Me Guest is an old name for JetBrains Client
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final String CWM_GUEST_PREFIX = "CodeWithMeGuest";
  public static final String JETBRAINS_CLIENT_PREFIX = "JetBrainsClient";
  public static final String GATEWAY_PREFIX = "Gateway";

  @SuppressWarnings("SSBasedInspection") private static final Set<String> COMMERCIAL_EDITIONS = new HashSet<>(Arrays.asList(
    IDEA_PREFIX, APPCODE_PREFIX, CLION_PREFIX, PYCHARM_PREFIX, DATASPELL_PREFIX, RUBY_PREFIX, PHP_PREFIX, WEB_PREFIX,
    DBE_PREFIX, RIDER_PREFIX, GOIDE_PREFIX, RUSTROVER_PREFIX, AQUA_PREFIX));

  public static @NotNull String getPlatformPrefix() {
    return getPlatformPrefix(IDEA_PREFIX);
  }

  public static String getPlatformPrefix(@Nullable String defaultPrefix) {
    return System.getProperty(PLATFORM_PREFIX_KEY, defaultPrefix);
  }

  public static void setDefaultPrefixForCE() {
    // IJ CE doesn't have prefix if we start IDE from the source code.
    // The proper fix is to set the prefix in all CE run configurations but for keeping compatibility set it indirectly
    System.setProperty(PLATFORM_PREFIX_KEY, getPlatformPrefix(IDEA_CE_PREFIX));
  }

  public static boolean isJetBrainsProduct() {
    ApplicationInfo appInfo = ApplicationInfo.getInstance();
    return appInfo != null && appInfo.getShortCompanyName().equals("JetBrains");
  }

  /**
   * If you're enabling some behavior in IntelliJ IDEA, it's quite probable that it makes sense to enable it in Android Studio as well,
   * so consider adding {@code || IdeInfo.isAndroidStudio()} condition.
   */
  public static boolean isIntelliJ() {
    return isIdeaUltimate() || isIdeaCommunity() || is(IDEA_EDU_PREFIX);
  }

  public static boolean isIdeaUltimate() {
    return is(IDEA_PREFIX);
  }

  /**
   * If you're enabling some behavior in IntelliJ IDEA, it's quite probable that it makes sense to enable it in Android Studio as well,
   * so consider adding {@code || IdeInfo.isAndroidStudio()} condition.
   */
  public static boolean isIdeaCommunity() {
    return is(IDEA_CE_PREFIX);
  }

  /**
   * @deprecated use other ways to customize behavior in different IDEs, see {@link PlatformUtils the class-level javadoc}
   */
  @Deprecated
  public static boolean isIdeaEducational() {
    return is(IDEA_EDU_PREFIX);
  }

  public static boolean isRubyMine() {
    return is(RUBY_PREFIX);
  }

  /**
   * see {@link com.jetbrains.cidr.PluginUtils CIDR-specific information}
   */
  public static boolean isAppCode() {
    return is(APPCODE_PREFIX);
  }

  public static boolean isAqua() {
    return is(AQUA_PREFIX);
  }

  /**
   * see {@link com.jetbrains.cidr.PluginUtils CIDR-specific information}
   */
  public static boolean isCLion() {
    return is(CLION_PREFIX);
  }

  /**
   * see {@link com.jetbrains.cidr.PluginUtils CIDR-specific information}
   */
  public static boolean isCidr() {
    return isAppCode() || isCLion();
  }

  public static boolean isMPS() {
    return is(MPS_PREFIX);
  }

  public static boolean isPyCharm() {
    return is(PYCHARM_PREFIX) || isPyCharmCommunity() || isPyCharmEducational() || isDataSpell();
  }

  /**
   * @deprecated use other ways to customize behavior in different IDEs, see {@link PlatformUtils the class-level javadoc}
   */
  @Deprecated
  public static boolean isPyCharmPro() {
    return is(PYCHARM_PREFIX);
  }

  public static boolean isPyCharmCommunity() {
    return is(PYCHARM_CE_PREFIX);
  }

  public static boolean isDataSpell() {
    return is(DATASPELL_PREFIX);
  }

  public static boolean isPyCharmEducational() {
    return is(PYCHARM_EDU_PREFIX);
  }

  public static boolean isPhpStorm() {
    return is(PHP_PREFIX);
  }

  public static boolean isWebStorm() {
    return is(WEB_PREFIX);
  }

  public static boolean isWriterSide() {
    return is(WRITERSIDE_PREFIX);
  }

  public static boolean isDataGrip() {
    return is(DBE_PREFIX);
  }

  public static boolean isRider() {
    return is(RIDER_PREFIX);
  }

  public static boolean isGoIde() {
    return is(GOIDE_PREFIX);
  }

  public static boolean isGitClient() {
    return is(GIT_CLIENT_PREFIX);
  }

  public static boolean isJetBrainsClient() { return is(JETBRAINS_CLIENT_PREFIX); }

  public static boolean isGateway() { return is(GATEWAY_PREFIX); }

  public static boolean isCommunityEdition() {
    return isIdeaCommunity() || isPyCharmCommunity();
  }

  public static boolean isCommercialEdition() {
    return COMMERCIAL_EDITIONS.contains(getPlatformPrefix());
  }

  public static boolean isFleetBackend() {
    return is(FLEET_PREFIX);
  }

  public static boolean isRustRover() {
    return is(RUSTROVER_PREFIX);
  }

  public static boolean isQodana() {
    return SystemProperties.getBooleanProperty("qodana.application", false);
  }

  private static boolean is(String idePrefix) {
    return idePrefix.equals(getPlatformPrefix());
  }
}
