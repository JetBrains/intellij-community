// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JsonPointerUtil {
  @NotNull
  public static String escapeForJsonPointer(@NotNull String name) {
    if (StringUtil.isEmptyOrSpaces(name)) {
      return URLUtil.encodeURIComponent(name);
    }
    return StringUtil.replace(StringUtil.replace(name, "~", "~0"), "/", "~1");
  }

  @NotNull
  public static String unescapeJsonPointerPart(@NotNull String part) {
    part = URLUtil.unescapePercentSequences(part);
    return StringUtil.replace(StringUtil.replace(part, "~0", "~"), "~1", "/");
  }

  public static boolean isSelfReference(@Nullable String ref) {
    return "#".equals(ref) || "#/".equals(ref) || StringUtil.isEmpty(ref);
  }

  @NotNull
  public static List<String> split(@NotNull String pointer) {
    return StringUtil.split(pointer, "/", true, false);
  }

  @NotNull
  public static String normalizeSlashes(@NotNull String ref) {
    return StringUtil.trimStart(ref.replace('\\', '/'), "/");
  }

  @NotNull
  public static String normalizeId(@NotNull String id) {
    id = id.endsWith("#") ? id.substring(0, id.length() - 1) : id;
    return id.startsWith("#") ? id.substring(1) : id;
  }
}
