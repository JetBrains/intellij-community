package com.intellij.json;

import com.intellij.json.psi.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

  @Nullable
  public static <T extends JsonElement> T getPropertyValueOfType(@NotNull final JsonObject object, @NotNull final String name,
                                                                 @NotNull final Class<T> clazz) {
    final JsonProperty property = object.findProperty(name);
    if (property == null) return null;
    return ObjectUtils.tryCast(property.getValue(), clazz);
  }

  @Nullable
  public static List<String> getChildAsStringList(@NotNull final JsonObject object, @NotNull final String name) {
    final JsonArray array = getPropertyValueOfType(object, name, JsonArray.class);
    if (array != null) return array.getValueList().stream().filter(value -> value instanceof JsonStringLiteral)
      .map(value -> StringUtil.unquoteString(value.getText())).collect(Collectors.toList());
    return null;
  }

  @Nullable
  public static List<String> getChildAsSingleStringOrList(@NotNull final JsonObject object, @NotNull final String name) {
    final List<String> list = getChildAsStringList(object, name);
    if (list != null) return list;
    final JsonStringLiteral literal = getPropertyValueOfType(object, name, JsonStringLiteral.class);
    return literal == null ? null : Collections.singletonList(StringUtil.unquoteString(literal.getText()));
  }

  public static boolean isArrayElement(@NotNull PsiElement element) {
    return element instanceof JsonValue && element.getParent() instanceof JsonArray;
  }

  public static int getArrayIndexOfItem(@NotNull PsiElement e) {
    PsiElement parent = e.getParent();
    if (!(parent instanceof JsonArray)) return -1;
    List<JsonValue> elements = ((JsonArray)parent).getValueList();
    for (int i = 0; i < elements.size(); i++) {
      if (e == elements.get(i)) {
        return i;
      }
    }
    return -1;
  }
}
