// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class JsonPointerUtil {
  @NotNull
  public static String escapeForJsonPointer(@NotNull String name) {
    return StringUtil.replace(StringUtil.replace(name, "~", "~0"), "/", "~1");
  }

  @NotNull
  public static String unescapeJsonPointerPart(@NotNull String part) {
    return StringUtil.replace(StringUtil.replace(part, "~0", "~"), "~1", "/");
  }
}
