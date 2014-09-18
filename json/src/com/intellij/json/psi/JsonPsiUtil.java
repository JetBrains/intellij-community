package com.intellij.json.psi;

import com.intellij.json.JsonElementTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.json.JsonParserDefinition.JSON_COMMENTARIES;

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

  /**
   * Find the furthest sibling element with the same type as given anchor.
   * <p/>
   * Ignore white spaces for any type of element except {@link com.intellij.json.JsonElementTypes#LINE_COMMENT}
   * where non indentation white space (that has new line in the middle) will stop the search.
   *
   * @param anchor element to start from
   * @param after  whether to scan through sibling elements forward or backward
   * @return described element or anchor if search stops immediately
   */
  @NotNull
  public static PsiElement findFurthestSiblingOfSameType(@NotNull PsiElement anchor, boolean after) {
    ASTNode node = anchor.getNode();
    // Compare by node type to distinguish between different types of comments
    final IElementType expectedType = node.getElementType();
    ASTNode lastSeen = node;
    while (node != null) {
      final IElementType elementType = node.getElementType();
      if (elementType == expectedType) {
        lastSeen = node;
      }
      else if (elementType == TokenType.WHITE_SPACE) {
        if (expectedType == JsonElementTypes.LINE_COMMENT && node.getText().indexOf('\n', 1) != -1) {
          break;
        }
      }
      else if (!JSON_COMMENTARIES.contains(elementType) || JSON_COMMENTARIES.contains(expectedType)) {
        break;
      }
      node = after ? node.getTreeNext() : node.getTreePrev();
    }
    return lastSeen.getPsi();
  }
}
