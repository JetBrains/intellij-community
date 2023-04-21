// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface LombokVersion {
  @NonNls String PLUGIN_NAME = "Lombok plugin";
  /**
   * Current version of lombok plugin
   */
  @NonNls String LAST_LOMBOK_VERSION = "1.18.26";

  @NonNls String LAST_LOMBOK_VERSION_WITH_JPS_FIX = "1.18.16";
  @NonNls String LOMBOK_VERSION_WITH_JDK16_FIX = "1.18.20";

  Pattern VERSION_PATTERN = Pattern.compile("(.*:)([\\d.]+)(.*)");

  static boolean isLessThan(@Nullable String currentVersion, @Nullable String otherVersion) {
    try {
      return StringUtil.compareVersionNumbers(currentVersion, otherVersion) < 0;
    }
    catch (NumberFormatException e) {
      Logger.getInstance(LombokVersion.class).info("Unable to parse lombok version: " + currentVersion);
      return false;
    }
  }

  @Nullable
  static String parseLombokVersion(@Nullable OrderEntry orderEntry) {
    String result = null;
    if (orderEntry != null) {
      final String presentableName = orderEntry.getPresentableName();
      final Matcher matcher = VERSION_PATTERN.matcher(presentableName);
      if (matcher.find()) {
        result = matcher.group(2);
      }
    }
    return result;
  }
}
