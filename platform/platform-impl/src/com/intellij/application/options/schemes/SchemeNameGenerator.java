// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.schemes;

import com.intellij.openapi.options.Scheme;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public final class SchemeNameGenerator {
  private final static String COPY_NAME_SUFFIX = "copy";

  private SchemeNameGenerator() {
  }

  public static String getUniqueName(@NotNull String preferredName, @NotNull Predicate<? super String> nameExistsPredicate) {
    if (nameExistsPredicate.test(preferredName)) {
      int numberPos = preferredName.length() - 1;
      while (numberPos >= 0 && Character.isDigit(preferredName.charAt(numberPos))) {
        numberPos--;
      }
      String baseName = numberPos >= 0 ? preferredName.substring(0, numberPos + 1) : preferredName;
      if (!baseName.endsWith(COPY_NAME_SUFFIX)) {
        baseName = preferredName + " " + COPY_NAME_SUFFIX;
      }
      if (!nameExistsPredicate.test(baseName)) return baseName;
      int i = 1;
      while (true) {
        String newName = baseName + i;
        if (!nameExistsPredicate.test(newName)) return newName;
        i++;
      }
    }
    return preferredName;
  }


  public static String getUniqueName(@Nullable String preferredName,
                                     @Nullable Scheme parentScheme,
                                     @NotNull Predicate<? super String> nameExistsPredicate) {
    assert preferredName != null || parentScheme != null : "Either preferredName or parentScheme must be non-null";
    String baseName = preferredName != null ? preferredName : parentScheme.getName();
    return getUniqueName(baseName, nameExistsPredicate);
  }
}
