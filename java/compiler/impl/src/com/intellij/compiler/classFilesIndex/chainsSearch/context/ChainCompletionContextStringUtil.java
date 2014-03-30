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
package com.intellij.compiler.classFilesIndex.chainsSearch.context;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
class ChainCompletionContextStringUtil {

  private ChainCompletionContextStringUtil(){}

  private final static int COMMON_PART_MIN_LENGTH = 3;

  public static boolean isSimilar(@NotNull final String varName,
                                  @NotNull final String parameterName) {
    final String sanitizedParamName = sanitizedToLowerCase(parameterName);
    if (StringUtil.commonPrefix(varName, sanitizedParamName).length() >= COMMON_PART_MIN_LENGTH) {
      return true;
    }
    final String suffix = StringUtil.commonSuffix(varName, sanitizedParamName);
    return suffix.length() >= COMMON_PART_MIN_LENGTH;
  }

  @NotNull
  public static String sanitizedToLowerCase(@NotNull final String name) {
    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      final char ch = name.charAt(i);
      if (Character.isLetter(ch)) {
        result.append(Character.toLowerCase(ch));
      }
    }
    return result.toString();
  }
}
