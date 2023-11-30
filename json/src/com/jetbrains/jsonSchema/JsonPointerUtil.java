// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JsonPointerUtil {
  public static @NotNull String escapeForJsonPointer(@NotNull String name) {
    if (StringUtil.isEmptyOrSpaces(name)) {
      return URLUtil.encodeURIComponent(name);
    }
    return StringUtil.replace(StringUtil.replace(name, "~", "~0"), "/", "~1");
  }

  public static @NotNull String unescapeJsonPointerPart(@NotNull String part) {
    part = URLUtil.unescapePercentSequences(part);
    return StringUtil.replace(StringUtil.replace(part, "~0", "~"), "~1", "/");
  }

  public static boolean isSelfReference(@Nullable String ref) {
    return "#".equals(ref) || "#/".equals(ref) || StringUtil.isEmpty(ref);
  }

  public static @NotNull List<String> split(@NotNull String pointer) {
    return StringUtil.split(pointer, "/", true, false);
  }

  public static @NotNull String normalizeSlashes(@NotNull String ref) {
    return StringUtil.trimStart(ref.replace('\\', '/'), "/");
  }

  public static @NotNull String normalizeId(@NotNull String id) {
    id = id.endsWith("#") ? id.substring(0, id.length() - 1) : id;
    return id.startsWith("#") ? id.substring(1) : id;
  }
}
