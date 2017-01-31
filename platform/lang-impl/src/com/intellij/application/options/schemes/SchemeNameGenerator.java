/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.application.options.schemes;

import com.intellij.openapi.options.Scheme;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public class SchemeNameGenerator {
  private final static String COPY_NAME_SUFFIX = "copy";

  private SchemeNameGenerator() {
  }

  public static String getUniqueName(@NotNull String preferredName, @NotNull Predicate<String> nameExistsPredicate) {
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
                                     @NotNull Predicate<String> nameExistsPredicate) {
    assert preferredName != null || parentScheme != null : "Either preferredName or parentScheme must be non-null";
    String baseName = preferredName != null ? preferredName : parentScheme.getName();
    return getUniqueName(baseName, nameExistsPredicate);
  }
}
