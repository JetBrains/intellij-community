// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * @author Alexander Lobas
 */
public final class PluginSiteUtils {
  private static final Pattern TAG_PATTERN =
    Pattern.compile("</?\\w+((\\s+\\w+(\\s*=\\s*(?:\".*?\"|'.*?'|[\\^'\">\\s]+))?)+\\s*|\\s*)/?>");
  private static final int SHORT_DESC_SIZE = 170;
  private static final Pattern BR_PATTERN = Pattern.compile("<br\\s*/?>");

  @Nullable
  public static String preparePluginDescription(@Nullable String s, boolean shortSize) {
    if (s == null || s.isEmpty()) {
      return null;
    }
    String description = prepareDescription(s, shortSize);
    return description.isEmpty() || description.endsWith(".") ? description : description + ".";
  }

  @NotNull
  private static String prepareDescription(@NotNull String s, boolean shortSize) {
    if (shortSize) {
      String[] split = BR_PATTERN.split(s);

      if (split.length > 1) {
        String sanitize = stripTags(split[0]);
        if (sanitize.length() <= SHORT_DESC_SIZE) return sanitize;
      }
    }

    String stripped = stripTags(s);

    if (shortSize) {
      for (String sep : new String[]{". ", ".\n", ": ", ":\n"}) {
        String by = substringBy(stripped, sep);
        if (by != null) return by;
      }

      if (stripped.length() > SHORT_DESC_SIZE) {
        stripped = stripped.substring(0, SHORT_DESC_SIZE);

        int index = stripped.lastIndexOf(' ');
        if (index == -1) {
          index = stripped.length();
        }

        stripped = stripped.substring(0, index) + "...";
      }
    }

    return stripped;
  }

  @Nullable
  private static String substringBy(@NotNull String str, @NotNull String separator) {
    int end = str.indexOf(separator);
    if (end > 0 && end <= SHORT_DESC_SIZE) {
      return str.substring(0, end + (separator.contains(":") ? 0 : separator.length())).trim();
    }
    return null;
  }

  @NotNull
  private static String stripTags(@NotNull String s) {
    return TAG_PATTERN.matcher(s).replaceAll("").trim();
  }
}