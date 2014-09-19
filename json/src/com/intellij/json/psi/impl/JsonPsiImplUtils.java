package com.intellij.json.psi.impl;

import com.intellij.json.JsonParserDefinition;
import com.intellij.json.psi.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JsonPsiImplUtils {
  @NotNull
  public static String getName(@NotNull JsonProperty property) {
    return StringUtil.stripQuotesAroundValue(property.getNameElement().getText());
  }

  /**
   * Actually only JSON string literal should be accepted as valid name of property according to standard,
   * but for compatibility with JavaScript integration any JSON literals as well as identifiers (unquoted words)
   * are possible and highlighted as error later.
   *
   * @see com.intellij.json.codeinsight.JsonStandardComplianceInspection
   */
  @NotNull
  public static JsonValue getNameElement(@NotNull JsonProperty property) {
    final PsiElement firstChild = property.getFirstChild();
    assert firstChild instanceof JsonLiteral || firstChild instanceof JsonReferenceExpression;
    return (JsonValue)firstChild;
  }

  @Nullable
  public static JsonValue getValue(@NotNull JsonProperty property) {
    return PsiTreeUtil.getNextSiblingOfType(getNameElement(property), JsonValue.class);
  }

  public static boolean isQuotedString(@NotNull JsonLiteral literal) {
    return literal.getNode().findChildByType(JsonParserDefinition.STRING_LITERALS) != null;
  }


  private static final String ourEscapesTable = "\"\"\\\\//b\bf\fn\nr\rt\t";

  @NotNull
  public static List<Pair<TextRange, String>> getTextFragments(@NotNull JsonStringLiteral literal) {
    final List<Pair<TextRange, String>> result = new ArrayList<Pair<TextRange, String>>();
    final String text = literal.getText();
    final int length = text.length();
    int pos = 1, unescapedSequenceStart = 1;
    while (pos < length) {
      if (text.charAt(pos) == '\\') {
        if (unescapedSequenceStart != pos) {
          result.add(Pair.create(new TextRange(unescapedSequenceStart, pos), text.substring(unescapedSequenceStart, pos)));
        }
        if (pos == length - 1) {
          result.add(Pair.create(new TextRange(pos, pos + 1), "\\"));
          break;
        }
        final char next = text.charAt(pos + 1);
        switch (next) {
          case '"':
          case '\\':
          case '/':
          case 'b':
          case 'f':
          case 'n':
          case 'r':
          case 't':
            final int idx = ourEscapesTable.indexOf(next);
            result.add(Pair.create(new TextRange(pos, pos + 2), ourEscapesTable.substring(idx + 1, idx + 2)));
            pos += 2;
            break;
          case 'u':
            int i = pos + 2;
            for (; i < pos + 6; i++) {
              if (i == length || !StringUtil.isHexDigit(text.charAt(i))) {
                break;
              }
            }
            result.add(Pair.create(new TextRange(pos, i), text.substring(pos, i)));
            pos = i;
            break;
          default:
            result.add(Pair.create(new TextRange(pos, pos + 2), text.substring(pos, pos + 2)));
            pos += 2;
        }
        unescapedSequenceStart = pos;
      }
      else {
        pos++;
      }
    }
    final int contentEnd = text.charAt(0) == text.charAt(length - 1) ? length - 1 : length;
    if (unescapedSequenceStart < contentEnd) {
      result.add(Pair.create(new TextRange(unescapedSequenceStart, length), text.substring(unescapedSequenceStart, contentEnd)));
    }
    return result;
  }

  public static void delete(@NotNull JsonProperty property) {
    final ASTNode myNode = property.getNode();
    JsonPsiChangeUtils.removeCommaSeparatedFromList(myNode, myNode.getTreeParent());
  }

  @Nullable
  public static JsonProperty findProperty(@NotNull JsonObject object, @NotNull String name) {
    final Collection<JsonProperty> properties = PsiTreeUtil.findChildrenOfType(object, JsonProperty.class);
    for (JsonProperty property : properties) {
      if (property.getName().equals(name)) {
        return property;
      }
    }
    return null;
  }
}
