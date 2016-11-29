package com.intellij.json.psi.impl;

import com.intellij.icons.AllIcons;
import com.intellij.json.JsonBundle;
import com.intellij.json.JsonParserDefinition;
import com.intellij.json.codeinsight.JsonStandardComplianceInspection;
import com.intellij.json.psi.*;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JsonPsiImplUtils {
  private static final Key<List<Pair<TextRange, String>>> STRING_FRAGMENTS = new Key<>("JSON string fragments");

  @NotNull
  public static String getName(@NotNull JsonProperty property) {
    return StringUtil.unescapeStringCharacters(JsonPsiUtil.stripQuotes(property.getNameElement().getText()));
  }

  /**
   * Actually only JSON string literal should be accepted as valid name of property according to standard,
   * but for compatibility with JavaScript integration any JSON literals as well as identifiers (unquoted words)
   * are possible and highlighted as error later.
   *
   * @see JsonStandardComplianceInspection
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

  @Nullable
  public static ItemPresentation getPresentation(@NotNull final JsonProperty property) {
    return new ItemPresentation() {
      @Nullable
      @Override
      public String getPresentableText() {
        return property.getName();
      }

      @Nullable
      @Override
      public String getLocationString() {
        return null;
      }

      @Nullable
      @Override
      public Icon getIcon(boolean unused) {
        if (property.getValue() instanceof JsonArray) {
          return AllIcons.Json.Property_brackets;
        }
        if (property.getValue() instanceof JsonObject) {
          return AllIcons.Json.Property_braces;
        }
        return PlatformIcons.PROPERTY_ICON;
      }
    };
  }

  @Nullable
  public static ItemPresentation getPresentation(@NotNull final JsonArray array) {
    return new ItemPresentation() {
      @Nullable
      @Override
      public String getPresentableText() {
        return JsonBundle.message("json.array");
      }

      @Nullable
      @Override
      public String getLocationString() {
        return null;
      }

      @Nullable
      @Override
      public Icon getIcon(boolean unused) {
        return AllIcons.Json.Array;
      }
    };
  }

  @Nullable
  public static ItemPresentation getPresentation(@NotNull final JsonObject object) {
    return new ItemPresentation() {
      @Nullable
      @Override
      public String getPresentableText() {
        return JsonBundle.message("json.object");
      }

      @Nullable
      @Override
      public String getLocationString() {
        return null;
      }

      @Nullable
      @Override
      public Icon getIcon(boolean unused) {
        return AllIcons.Json.Object;
      }
    };
  }

  private static final String ourEscapesTable = "\"\"\\\\//b\bf\fn\nr\rt\t";

  @NotNull
  public static List<Pair<TextRange, String>> getTextFragments(@NotNull JsonStringLiteral literal) {
    List<Pair<TextRange, String>> result = literal.getUserData(STRING_FRAGMENTS);
    if (result == null) {
      result = new ArrayList<>();
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
      result = Collections.unmodifiableList(result);
      literal.putUserData(STRING_FRAGMENTS, result);
    }
    return result;
  }

  public static void delete(@NotNull JsonProperty property) {
    final ASTNode myNode = property.getNode();
    JsonPsiChangeUtils.removeCommaSeparatedFromList(myNode, myNode.getTreeParent());
  }

  @NotNull
  public static String getValue(@NotNull JsonStringLiteral literal) {
    return StringUtil.unescapeStringCharacters(JsonPsiUtil.stripQuotes(literal.getText()));
  }

  public static boolean getValue(@NotNull JsonBooleanLiteral literal) {
    return literal.textMatches("true");
  }

  public static double getValue(@NotNull JsonNumberLiteral literal) {
    return Double.parseDouble(literal.getText());
  }
}
