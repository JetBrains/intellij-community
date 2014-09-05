package com.intellij.json.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Various helper methods for working with PSI of JSON language.
 *
 * @author Mikhail Golubev
 */
@SuppressWarnings("UnusedDeclaration")
public class JsonPsiUtil {
  private JsonPsiUtil() {
    // empty
  }


  /**
   * Checks that PSI element represents item of JSON array.
   *
   * @param element PSI element to check
   * @return whether this PSI element is array element
   */
  public static boolean isArrayElement(@NotNull PsiElement element) {
    return element instanceof JsonValue && element.getParent() instanceof JsonArray;
  }

  /**
   * Checks that PSI element represents key of JSON property (key-value pair of JSON object)
   *
   * @param element PSI element to check
   * @return whether this PSI element is property key
   */
  public static boolean isPropertyKey(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    return parent instanceof JsonProperty && element == ((JsonProperty)parent).getNameElement();
  }

  /**
   * Checks that PSI element represents value of JSON property (key-value pair of JSON object)
   *
   * @param element PSI element to check
   * @return whether this PSI element is property value
   */
  public static boolean isPropertyValue(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    return parent instanceof JsonProperty && element == ((JsonProperty)parent).getValue();
  }
}
