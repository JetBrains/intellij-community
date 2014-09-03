/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"deprecation", "UnusedDeclaration"})
public class PlatformUtils {
  public static final String PLATFORM_PREFIX_KEY = PlatformUtilsCore.PLATFORM_PREFIX_KEY;

  public static final String IDEA_PREFIX = PlatformUtilsCore.IDEA_PREFIX;
  public static final String IDEA_CE_PREFIX = PlatformUtilsCore.COMMUNITY_PREFIX;
  public static final String APPCODE_PREFIX = PlatformUtilsCore.APPCODE_PREFIX;
  public static final String CLION_PREFIX = PlatformUtilsCore.CLION_PREFIX;
  public static final String PYCHARM_PREFIX = PlatformUtilsCore.PYCHARM_PREFIX;
  public static final String PYCHARM_CE_PREFIX = PlatformUtilsCore.PYCHARM_PREFIX2;
  public static final String RUBY_PREFIX = PlatformUtilsCore.RUBY_PREFIX;
  public static final String PHP_PREFIX = PlatformUtilsCore.PHP_PREFIX;
  public static final String WEB_PREFIX = PlatformUtilsCore.WEB_PREFIX;
  public static final String DBE_PREFIX = PlatformUtilsCore.DBE_PREFIX;

  private PlatformUtils() { }

  public static String getPlatformPrefix() {
    return PlatformUtilsCore.getPlatformPrefix();
  }

  public static String getPlatformPrefix(String defaultPrefix) {
    return PlatformUtilsCore.getPlatformPrefix(defaultPrefix);
  }

  public static boolean isIntelliJ() {
    return PlatformUtilsCore.isIntelliJ();
  }

  public static boolean isIdeaUltimate() {
    return PlatformUtilsCore.isIdea();
  }

  public static boolean isIdeaCommunity() {
    return PlatformUtilsCore.isCommunity();
  }

  public static boolean isRubyMine() {
    return PlatformUtilsCore.isRubyMine();
  }

  public static boolean isAppCode() {
    return PlatformUtilsCore.isAppCode();
  }

  public static boolean isCLion() {
    return PlatformUtilsCore.isCLion();
  }

  public static boolean isCidr() {
    return isAppCode() || isCLion();
  }

  public static boolean isPyCharm() {
    return PlatformUtilsCore.isPyCharm();
  }

  public static boolean isPyCharmPro() {
    return PlatformUtilsCore.isPyCharmPro();
  }

  public static boolean isPyCharmCommunity() {
    return PlatformUtilsCore.isPyCharmCommunity();
  }

  public static boolean isPhpStorm() {
    return PlatformUtilsCore.isPhpStorm();
  }

  public static boolean isWebStorm() {
    return PlatformUtilsCore.isWebStorm();
  }

  public static boolean isDatabaseIDE() {
    return PlatformUtilsCore.isDatabaseIDE();
  }

  public static boolean isCommunityEdition() {
    return isIdeaCommunity() || isPyCharmCommunity();
  }

  /** @deprecated not a common API; use DevKit's PsiUtil.isIdeaProject() when needed (to remove in IDEA 14) */
  @SuppressWarnings("UnusedDeclaration")
  public static boolean isIdeaProject(@Nullable Project project) { return false; }

  /** @deprecated use {@link #IDEA_CE_PREFIX} (to remove in IDEA 15) */
  @SuppressWarnings("UnusedDeclaration")
  public static final String COMMUNITY_PREFIX = IDEA_CE_PREFIX;

  /** @deprecated use {@link #isIdeaUltimate()} (to remove in IDEA 15) */
  @SuppressWarnings("UnusedDeclaration")
  public static boolean isIdea() { return isIdeaUltimate(); }

  /** @deprecated use {@link #isIdeaCommunity()} (to remove in IDEA 15) */
  @SuppressWarnings("UnusedDeclaration")
  public static boolean isCommunity() { return isIdeaCommunity(); }

  /** @deprecated use {@link #PYCHARM_CE_PREFIX} (to remove in IDEA 14) */
  @SuppressWarnings("UnusedDeclaration")
  public static final String PYCHARM_PREFIX2 = PYCHARM_CE_PREFIX;

  /** @deprecated to remove in IDEA 14 */
  @SuppressWarnings("UnusedDeclaration")
  public static final String FLEX_PREFIX = PlatformUtilsCore.FLEX_PREFIX;

  /** @deprecated to remove in IDEA 14 */
  @SuppressWarnings("UnusedDeclaration")
  public static boolean isFlexIde() { return false; }
}
