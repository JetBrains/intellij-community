package com.intellij.json.psi;

import com.intellij.json.JsonElementTypes;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

  /**
   * Check that element type of the given AST node belongs to the token set.
   * <p/>
   * It slightly less verbose than {@code set.contains(node.getElementType())} and overloaded methods with the same name
   * allow check ASTNode/PsiElement against both concrete element types and token sets in uniform way.
   */
  public static boolean hasElementType(@NotNull ASTNode node, @NotNull TokenSet set) {
    return set.contains(node.getElementType());
  }

  /**
   * @see #hasElementType(com.intellij.lang.ASTNode, com.intellij.psi.tree.TokenSet)
   */
  public static boolean hasElementType(@NotNull ASTNode node, IElementType... types) {
    return hasElementType(node, TokenSet.create(types));
  }

  /**
   * @see #hasElementType(com.intellij.lang.ASTNode, com.intellij.psi.tree.TokenSet)
   */
  public static boolean hasElementType(@NotNull PsiElement element, @NotNull TokenSet set) {
    return element.getNode() != null && hasElementType(element.getNode(), set);
  }

  /**
   * @see #hasElementType(com.intellij.lang.ASTNode, com.intellij.psi.tree.IElementType...)
   */
  public static boolean hasElementType(@NotNull PsiElement element, IElementType... types) {
    return element.getNode() != null && hasElementType(element.getNode(), types);
  }

  /**
   * Returns text of the given PSI element. Unlike obvious {@link PsiElement#getText()} this method unescapes text of the element if latter
   * belongs to injected code fragment using {@link InjectedLanguageManager#getUnescapedText(PsiElement)}.
   *
   * @param element PSI element which text is needed
   * @return text of the element with any host escaping removed
   */
  @NotNull
  public static String getElementTextWithoutHostEscaping(@NotNull PsiElement element) {
    final InjectedLanguageManager manager = InjectedLanguageManager.getInstance(element.getProject());
    if (manager.isInjectedFragment(element.getContainingFile())) {
      return manager.getUnescapedText(element);
    }
    else {
      return element.getText();
    }
  }

  /**
   * Returns content of the string literal (without escaping) striving to preserve as much of user data as possible.
   * <ul>
   * <li>If literal length is greater than one and it starts and ends with the same quote and the last quote is not escaped, returns
   * text without first and last characters.</li>
   * <li>Otherwise if literal still begins with a quote, returns text without first character only.</li>
   * <li>Returns unmodified text in all other cases.</li>
   * </ul>
   *
   * @param text presumably result of {@link JsonStringLiteral#getText()}
   * @return
   */
  @NotNull
  public static String stripQuotes(@NotNull String text) {
    if (text.length() > 0) {
      final char firstChar = text.charAt(0);
      final char lastChar = text.charAt(text.length() - 1);
      if (firstChar == '\'' || firstChar == '"') {
        if (text.length() > 1 && firstChar == lastChar && !isEscapedChar(text, text.length() - 1)) {
          return text.substring(1, text.length() - 1);
        }
        return text.substring(1);
      }
    }
    return text;
  }

  /**
   * Checks that character in given position is escaped with backslashes.
   *
   * @param text     text character belongs to
   * @param position position of the character
   * @return whether character at given position is escaped, i.e. preceded by odd number of backslashes
   */
  public static boolean isEscapedChar(@NotNull String text, int position) {
    int count = 0;
    for (int i = position - 1; i >= 0 && text.charAt(i) == '\\'; i--) {
      count++;
    }
    return count % 2 != 0;
  }

  /**
   * Add new property and necessary comma either at the beginning of the object literal or at its end.
   *
   * @param object   object literal
   * @param property new property, probably created via {@link JsonElementGenerator}
   * @param first    if true make new property first in the object, otherwise append in the end of property list
   * @return property as returned by {@link PsiElement#addAfter(PsiElement, PsiElement)}
   */
  @NotNull
  public static PsiElement addProperty(@NotNull JsonObject object, @NotNull JsonProperty property, boolean first) {
    final List<JsonProperty> propertyList = object.getPropertyList();
    if (!first) {
      final JsonProperty lastProperty = ContainerUtil.getLastItem(propertyList);
      if (lastProperty != null) {
        final PsiElement addedProperty = object.addAfter(property, lastProperty);
        object.addBefore(new JsonElementGenerator(object.getProject()).createComma(), addedProperty);
        return addedProperty;
      }
    }
    final PsiElement leftBrace = object.getFirstChild();
    assert hasElementType(leftBrace, JsonElementTypes.L_CURLY);
    final PsiElement addedProperty = object.addAfter(property, leftBrace);
    if (!propertyList.isEmpty()) {
      object.addAfter(new JsonElementGenerator(object.getProject()).createComma(), addedProperty);
    }
    return addedProperty;
  }
}
