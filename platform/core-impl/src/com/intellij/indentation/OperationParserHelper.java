/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.indentation;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Andrey.Vokin
 * Date: 3/23/12
 */
public class OperationParserHelper {
  private static boolean parsePostfixOperation(@NotNull final BinaryOperationParser parser) {
    final PsiBuilder.Marker tempMarker = parser.mark();
    PsiBuilder.Marker lastMarker = tempMarker;
    boolean result = parsePrefixOperation(parser);
    boolean tempMarkerDeleted = false;
    while (parser.getPostfixOperators().contains(parser.getTokenType()) &&
           !parser.getWhitespaceTokenSet().contains(parser.rawLookup(-1)) && !parser.isNewLine()) {
      final PsiBuilder.Marker operationMarker = lastMarker.precede();
      if (!tempMarkerDeleted) {
        tempMarker.drop();
        tempMarkerDeleted = true;
      }
      lastMarker = operationMarker;
      parser.advance();
      parser.done(operationMarker, parser.getPostfixExpressionElementType());
      result = true;
    }
    if (!tempMarkerDeleted) {
      tempMarker.drop();
    }
    return result;
  }

  private static boolean parsePrefixOperation(@NotNull final BinaryOperationParser parser) {
    int prefixCount = 0;
    while (parser.getPrefixOperators().contains(parser.lookAhead(prefixCount))) {
      prefixCount++;
    }
    final PsiBuilder.Marker[] prefixMarkers = new PsiBuilder.Marker[prefixCount];
    final IElementType[] elementTypes = new IElementType[prefixCount];
    for (int i = 0; i < prefixCount; i++) {
      prefixMarkers[i] = parser.mark();
      elementTypes[i] = parser.getPrefixExpressionElementType();
      parser.advance();
    }
    final boolean result = parser.parseSimpleExpression() || prefixCount > 0;
    for (int i = prefixCount - 1; i >= 0; i--) {
      parser.done(prefixMarkers[i], elementTypes[i]);
    }
    return result;
  }

  public static boolean callParsingBinaryOperation(@NotNull final BinaryOperationParser parser, int level) {
    if (level < 0) {
      return parsePostfixOperation(parser);
    }
    return parseBinaryOperation(parser, level);
  }

  private static boolean isBinaryOperator(@NotNull final BinaryOperationParser parser, int level) {
    if (parser instanceof CustomBinaryOperationParser) {
      return ((CustomBinaryOperationParser)parser).isBinaryOperator(level);
    }
    final IElementType tokenType = parser.getTokenType();
    return parser.getOperatorsByPriority()[level].contains(tokenType);
  }

  private static void parseBinaryOperator(@NotNull final BinaryOperationParser parser) {
    if (parser instanceof CustomBinaryOperationParser) {
      ((CustomBinaryOperationParser)parser).parseBinaryOperator();
    } else {
      parser.advance();
    }
  }

  /**
   * Parses arithmetic mult, arithmetic sum, bit operation, relation operation
   * @param level 0 for mult, 1, for sum, 2 for bit, 3 for relations
   */
  private static boolean parseBinaryOperation(@NotNull final BinaryOperationParser parser, int level) {
    final PsiBuilder.Marker tempMarker = parser.mark();
    PsiBuilder.Marker lastMarker = tempMarker;
    boolean result = callParsingBinaryOperation(parser, level - 1);
    boolean tempMarkerDeleted = false;
    while (isBinaryOperator(parser, level) && !parser.isNewLine()) {
      final PsiBuilder.Marker operationMarker = lastMarker.precede();
      if (!tempMarkerDeleted) {
        tempMarker.drop();
        tempMarkerDeleted = true;
      }
      lastMarker = operationMarker;
      parseBinaryOperator(parser);
      callParsingBinaryOperation(parser, level - 1);
      parser.done(operationMarker, parser.getOperationElementTypes()[level]);
      result = true;
    }
    if (!tempMarkerDeleted) {
      tempMarker.drop();
    }
    return result;
  }

  public interface BinaryOperationParser {
    /**
     * Gets the TokenType from PsiBuilder
     * @return IElementType of current element
     */
    IElementType getTokenType();

    /**
     * Checks current token starts the line
     * @return true if new line
     */
    boolean isNewLine();

    /**
     * Advance current position of PsiBuilder
     */
    void advance();

    /**
     * See what token type is in <code>step</code> ahead / benind (including whitespaces)
     * @param step 0 is current token, -1 is previous, 1 is next and so on
     * @return IElementType of the required element
     */
    IElementType rawLookup(int step);

    /**
     * See what token type is in <code>step</code> ahead (not including whitespaces)
     * @param step 0 is current token, 1 is next and so on
     * @return IElementType of the required element
     */
    IElementType lookAhead(int step);

    /**
     * Create new marker
     * @return PsiBuilder.Marker of created marker
     */
    PsiBuilder.Marker mark();

    /**
     * Close marker with element type
     * @param marker to close
     * @param elementType to close marker as
     */
    void done(PsiBuilder.Marker marker, IElementType elementType);

    /**
     * Parses operand
     * @return boolean if success
     */
    boolean parseSimpleExpression();

    /**
     * Provides all whitespace tokens
     * @return TokenSet of whitespaces
     */
    TokenSet getWhitespaceTokenSet();

    /**
     * Provides prefix operators
     * @return TokenSet of prefix operators
     */
    TokenSet getPrefixOperators();

    /**
     * Provides postfix operators
     * @return TokenSet of prefix operators
     */
    TokenSet getPostfixOperators();

    /**
     * Provides operation priority and operands
     * @return array of TokenSets
     */
    TokenSet[] getOperatorsByPriority();

    /**
     * Provides element types to finish postfix element marker
     * @return IElementType
     */
    @Nullable
    IElementType getPostfixExpressionElementType();

    /**
     * Provides element types to finish prefix element marker
     * @return IElementType
     */
    @Nullable
    IElementType getPrefixExpressionElementType();

    /**
     * Provides element types to finish binary operation element
     * @return array of Element Types
     */
    IElementType[] getOperationElementTypes();
  }

  public interface CustomBinaryOperationParser {
    boolean isBinaryOperator(int level);

    void parseBinaryOperator();
  }
}
