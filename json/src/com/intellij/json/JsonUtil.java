package com.intellij.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonUtil {
  private JsonUtil() {
    // empty
  }

  /**
   * Clone of C# "as" operator.
   * Checks if expression has correct type and casts it if it has. Returns null otherwise.
   * It saves coder from "instanceof / cast" chains.
   *
   * Copied from PyCharm's {@code PyUtil}.
   *
   * @param expression expression to check
   * @param cls        class to cast
   * @param <T>        class to cast
   * @return expression casted to appropriate type (if could be casted). Null otherwise.
   */
  @Nullable
  @SuppressWarnings("unchecked")
  public static <T> T as(@Nullable final Object expression, @NotNull final Class<T> cls) {
    if (expression == null) {
      return null;
    }
    if (cls.isAssignableFrom(expression.getClass())) {
      return (T)expression;
    }
    return null;
  }
}
