/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

public class PlatformUtilsCore {
  public static final String PLATFORM_PREFIX_KEY = "idea.platform.prefix";
  public static final String IDEA_PREFIX = "idea";
  public static final String COMMUNITY_PREFIX = "Idea";
  public static final String APPCODE_PREFIX = "AppCode";
  public static final String CPP_PREFIX = "CppIde";
  public static final String PYCHARM_PREFIX = "Python";
  public static final String PYCHARM_PREFIX2 = "PyCharm";
  public static final String RUBY_PREFIX = "Ruby";
  public static final String PHP_PREFIX = "PhpStorm";
  public static final String WEB_PREFIX = "WebStorm";
  public static final String FLEX_PREFIX = "Flex";

  public static String getPlatformPrefix() {
    return getPlatformPrefix(IDEA_PREFIX);
  }

  public static String getPlatformPrefix(String defaultPrefix) {
    return System.getProperty(PLATFORM_PREFIX_KEY, defaultPrefix);
  }

  public static boolean isIdea() {
    return IDEA_PREFIX.equals(getPlatformPrefix());
  }

  public static boolean isCommunity() {
    return COMMUNITY_PREFIX.equals(getPlatformPrefix());
  }

  public static boolean isRubyMine() {
    return RUBY_PREFIX.equals(getPlatformPrefix());
  }

  public static boolean isAppCode() {
    return APPCODE_PREFIX.equals(getPlatformPrefix());
  }

  public static boolean isCppIde() {
    return CPP_PREFIX.equals(getPlatformPrefix());
  }

  public static boolean isPyCharm() {
    String prefix = getPlatformPrefix();
    return PYCHARM_PREFIX.equals(prefix) || (prefix != null && prefix.startsWith(PYCHARM_PREFIX2));
  }

  public static boolean isPhpStorm() {
    return PHP_PREFIX.equals(getPlatformPrefix());
  }

  public static boolean isWebStorm() {
    return WEB_PREFIX.equals(getPlatformPrefix());
  }

  public static boolean isIntelliJ() {
    return isIdea() || isCommunity();
  }
}
