// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.ApplicationInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * This class allows changing behavior of the platform in specific IDEs. But if its methods are used for something it means that third-party
 * IDEs not listed here won't be able to get the desired behavior. So <strong>it's strongly not recommended to use methods from this class</strong>.
 * If you need to customize behavior of the platform somewhere, you should create a special application service for that and override it in
 * a specific IDE (look at {@link com.intellij.lang.IdeLanguageCustomization} and {@link com.intellij.openapi.updateSettings.UpdateStrategyCustomization}
 * for example).
 *
 * @author Konstantin Bulenkov, Nikolay Chashnikov
 */
public final class PlatformUtils {
  public static final String PLATFORM_PREFIX_KEY = "idea.platform.prefix";

  // NOTE: If you add any new prefixes to this list, please update the IntelliJPlatformProduct class in DevKit plugin
  public static final String IDEA_PREFIX = "idea";
  public static final String IDEA_CE_PREFIX = "Idea";
  public static final String IDEA_EDU_PREFIX = "IdeaEdu";
  public static final String APPCODE_PREFIX = "AppCode";
  public static final String CLION_PREFIX = "CLion";
  public static final String PYCHARM_PREFIX = "Python";
  public static final String PYCHARM_CE_PREFIX = "PyCharmCore";
  public static final String PYCHARM_EDU_PREFIX = "PyCharmEdu";
  public static final String RUBY_PREFIX = "Ruby";
  public static final String PHP_PREFIX = "PhpStorm";
  public static final String WEB_PREFIX = "WebStorm";
  public static final String DBE_PREFIX = "DataGrip";
  public static final String RIDER_PREFIX = "Rider";
  public static final String GOIDE_PREFIX = "GoLand";

  private static final Set<String> COMMERCIAL_EDITIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
    IDEA_PREFIX, APPCODE_PREFIX, CLION_PREFIX, PYCHARM_PREFIX, RUBY_PREFIX, PHP_PREFIX, WEB_PREFIX, DBE_PREFIX,
    RIDER_PREFIX, GOIDE_PREFIX
  )));

  public static String getPlatformPrefix() {
    return getPlatformPrefix(IDEA_PREFIX);
  }

  public static String getPlatformPrefix(String defaultPrefix) {
    return System.getProperty(PLATFORM_PREFIX_KEY, defaultPrefix);
  }

  public static boolean isJetBrainsProduct() {
    final ApplicationInfo appInfo = ApplicationInfo.getInstance();
    return appInfo != null && appInfo.getShortCompanyName().equals("JetBrains");
  }

  public static boolean isIntelliJ() {
    return isIdeaUltimate() || isIdeaCommunity() || isIdeaEducational();
  }

  public static boolean isIdeaUltimate() {
    return is(IDEA_PREFIX);
  }

  public static boolean isIdeaCommunity() {
    return is(IDEA_CE_PREFIX);
  }

  private static boolean isIdeaEducational() {
    return is(IDEA_EDU_PREFIX);
  }

  public static boolean isRubyMine() {
    return is(RUBY_PREFIX);
  }

  public static boolean isAppCode() {
    return is(APPCODE_PREFIX);
  }

  public static boolean isCLion() {
    return is(CLION_PREFIX);
  }

  public static boolean isCidr() {
    return isAppCode() || isCLion();
  }

  public static boolean isPyCharm() {
    return isPyCharmPro() || isPyCharmCommunity() || isPyCharmEducational();
  }

  public static boolean isPyCharmPro() {
    return is(PYCHARM_PREFIX);
  }

  public static boolean isPyCharmCommunity() {
    return is(PYCHARM_CE_PREFIX);
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

  public static boolean isDataGrip() {
    return is(DBE_PREFIX);
  }

  public static boolean isRider() {
    return is(RIDER_PREFIX);
  }

  public static boolean isGoIde() {
    return is(GOIDE_PREFIX);
  }

  public static boolean isCommunityEdition() {
    return isIdeaCommunity() || isPyCharmCommunity();
  }

  public static boolean isCommercialEdition() {
    return COMMERCIAL_EDITIONS.contains(getPlatformPrefix());
  }

  private static boolean is(String idePrefix) {
    return idePrefix.equals(getPlatformPrefix());
  }
}