package com.intellij.codeInsight.completion.methodChains.completion.context;

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
