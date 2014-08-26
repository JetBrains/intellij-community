package com.intellij.json;

import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonValue;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
@SuppressWarnings("UnusedDeclaration")
public class JsonUtil {
  private JsonUtil() {
    // empty
  }


  /**
   * Checks that PSI element represents item of JSON array.
   *
   * @param element PSI element to check
   * @return whether this PSI element is array element
   */
  private static boolean isArrayElement(@NotNull PsiElement element) {
    return element instanceof JsonValue && element.getParent() instanceof JsonArray;
  }
}
