// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

public final class UriUtil {
  private static final String URI_SEPARATOR_CHARS = "?#;";

  private UriUtil() {
  }

  public static @NotNull String trimTrailingSlashes(@NotNull String url) {
    return StringUtil.trimTrailing(url, '/');
  }

  public static @NotNull String trimLeadingSlashes(@NotNull String url) {
    return StringUtil.trimLeading(url, '/');
  }

  public static String trimParameters(@NotNull String url) {
    int end = Strings.indexOfAny(url, URI_SEPARATOR_CHARS);
    return end != -1 ? url.substring(0, end) : url;
  }

  /**
   * Splits the url into 2 parts: the scheme ("http", for instance) and the rest of the URL. <br/>
   * Scheme separator is not included neither to the scheme part, nor to the url part. <br/>
   * The scheme can be absent, in which case empty string is written to the first item of the Pair.
   */
  public static @NotNull Couple<String> splitScheme(@NotNull String url) {
    int index = url.indexOf(URLUtil.SCHEME_SEPARATOR);
    if (index == -1) {
      return Couple.of("", url);
    }
    return Couple.of(url.substring(0, index), url.substring(index+URLUtil.SCHEME_SEPARATOR.length()));
  }
}