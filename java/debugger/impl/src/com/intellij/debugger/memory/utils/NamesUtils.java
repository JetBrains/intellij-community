// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.utils;

import com.intellij.openapi.util.text.StringUtil;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public final class NamesUtils {
  public static @NotNull String getUniqueName(@NotNull ObjectReference ref) {
    String shortName = StringUtil.getShortName(ref.referenceType().name());
    String name = shortName.replace("[]", "Array");
    return String.format("%s@%d", name, ref.uniqueID());
  }

  static @NotNull String getArrayUniqueName(@NotNull ArrayReference ref) {
    String shortName = StringUtil.getShortName(ref.referenceType().name());
    int length = ref.length();

    String name = shortName.replaceFirst(Pattern.quote("[]"), String.format("[%d]", length));
    return String.format("%s@%d", name, ref.uniqueID());
  }
}
