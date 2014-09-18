package com.intellij.json.psi;

import com.intellij.json.JsonElementTypes;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
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

  /**
   * Find largest region of successive line comments that includes given one.
   * @param anchor anchor line comment from which range will be expanded
   * @return specified range of comments or pair {@code (anchor, anchor)} if anchor is not line comment
   */
  @NotNull
  public static Couple<PsiElement> expandLineCommentsRange(@NotNull PsiElement anchor) {
    return Couple.of(findOutermostLineComment(anchor, false), findOutermostLineComment(anchor, true));
  }

  /**
   * Lowest or topmost successive line comment for given anchor element.
   * @param anchor start element (most probably another line comment)
   * @param below whether to scan through element forward or backward
   * @return described {@link com.intellij.psi.PsiComment} element or {@code anchor} if it's not a line comment
   */
  @NotNull
  public static PsiElement findOutermostLineComment(@NotNull PsiElement anchor, boolean below) {
    ASTNode node = anchor.getNode();
    ASTNode lastSeenComment = node;
    while (node != null) {
      final IElementType elementType = node.getElementType();
      if (elementType == JsonElementTypes.LINE_COMMENT) {
        lastSeenComment = node;
      }
      else if (elementType != TokenType.WHITE_SPACE || node.getText().indexOf('\n', 1) != -1) {
        break;
      }
      node = below ? node.getTreeNext() : node.getTreePrev();
    }
    return lastSeenComment.getPsi();
  }
}
