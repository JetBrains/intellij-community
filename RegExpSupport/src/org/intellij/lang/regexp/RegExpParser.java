/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LightPsiParser;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.intellij.lang.regexp.psi.impl.RegExpCharImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class RegExpParser implements PsiParser, LightPsiParser {
  private static final TokenSet PROPERTY_TOKENS = TokenSet.create(RegExpTT.NUMBER, RegExpTT.COMMA, RegExpTT.NAME, RegExpTT.RBRACE);
  private final EnumSet<RegExpCapability> myCapabilities;

  public RegExpParser(EnumSet<RegExpCapability> capabilities) {
    myCapabilities = capabilities;
  }

  @Override
  public void parseLight(IElementType root, PsiBuilder builder) {
    final PsiBuilder.Marker rootMarker = builder.mark();

    while (true) {
      parsePattern(builder);
      if (builder.eof()) break;
      patternExpected(builder);
      if (builder.eof()) break;
    }

    rootMarker.done(root);
  }

  @Override
  @NotNull
  public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    parseLight(root, builder);
    return builder.getTreeBuilt();
  }


  /**
   * PATTERN ::= BRANCH "|" PATTERN | BRANCH
   */
  protected void parsePattern(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();

    parseBranch(builder);

    while (builder.getTokenType() == RegExpTT.UNION) {
      builder.advanceLexer();
      parseBranch(builder);
    }

    marker.done(RegExpElementTypes.PATTERN);
  }

  /**
   * BRANCH  ::= ATOM BRANCH | ""
   */
  private void parseBranch(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();

    while (!parseAtom(builder)) {
      final IElementType token = builder.getTokenType();
      if (token == RegExpTT.GROUP_END || token == RegExpTT.UNION || token == null) {
        // empty branches are allowed
        marker.done(RegExpElementTypes.BRANCH);
        return;
      }
      patternExpected(builder);
    }

    //noinspection StatementWithEmptyBody
    while (parseAtom(builder)) {}

    marker.done(RegExpElementTypes.BRANCH);
  }

  /**
   * ATOM        ::= CLOSURE | GROUP
   * CLOSURE     ::= GROUP QUANTIFIER
   */
  private boolean parseAtom(PsiBuilder builder) {
    final PsiBuilder.Marker marker = parseGroup(builder);

    if (marker == null) {
      return false;
    }
    final PsiBuilder.Marker marker2 = marker.precede();

    if (parseQuantifier(builder)) {
      marker2.done(RegExpElementTypes.CLOSURE);
    }
    else {
      marker2.drop();
    }

    return true;
  }

  /**
   * QUANTIFIER   ::= Q TYPE | ""
   * Q            ::= "{" BOUND "}" | "*" | "?" | "+"
   * BOUND        ::= NUM | NUM "," | NUM "," NUM
   * TYPE         ::= "?" | "+" | ""
   */
  private boolean parseQuantifier(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();

    if (builder.getTokenType() == RegExpTT.LBRACE) {
      builder.advanceLexer();
      final boolean minOmitted = builder.getTokenType() == RegExpTT.COMMA &&
                                 myCapabilities.contains(RegExpCapability.OMIT_NUMBERS_IN_QUANTIFIERS);
      if (minOmitted) {
        builder.advanceLexer();
      }
      else {
        if (builder.getTokenType() == RegExpTT.NUMBER) {
          final PsiBuilder.Marker numberMark = builder.mark();
          builder.advanceLexer();
          numberMark.done(RegExpElementTypes.NUMBER);
        }
        else {
          builder.error(RegExpBundle.message("parse.error.number.expected"));
        }
      }
      if (builder.getTokenType() == RegExpTT.RBRACE) {
        builder.advanceLexer();
        parseQuantifierType(builder);
        marker.done(RegExpElementTypes.QUANTIFIER);
      }
      else {
        if (!minOmitted) {
          checkMatches(builder, RegExpTT.COMMA, RegExpBundle.message("parse.error.comma.expected"));
        }
        if (builder.getTokenType() == RegExpTT.RBRACE) {
          builder.advanceLexer();
          parseQuantifierType(builder);
          marker.done(RegExpElementTypes.QUANTIFIER);
        }
        else if (builder.getTokenType() == RegExpTT.NUMBER) {
          final PsiBuilder.Marker numberMark = builder.mark();
          builder.advanceLexer();
          numberMark.done(RegExpElementTypes.NUMBER);
          checkMatches(builder, RegExpTT.RBRACE, RegExpBundle.message("parse.error.closing.brace.expected"));
          parseQuantifierType(builder);
          marker.done(RegExpElementTypes.QUANTIFIER);
        }
        else {
          builder.error(RegExpBundle.message("parse.error.closing.brace.or.number.expected"));
          marker.done(RegExpElementTypes.QUANTIFIER);
          return true;
        }
      }
    }
    else if (RegExpTT.QUANTIFIERS.contains(builder.getTokenType())) {
      builder.advanceLexer();
      parseQuantifierType(builder);
      marker.done(RegExpElementTypes.QUANTIFIER);
    }
    else {
      marker.drop();
      return false;
    }

    return true;
  }

  private static void parseQuantifierType(PsiBuilder builder) {
    if (builder.getTokenType() == RegExpTT.PLUS || builder.getTokenType() == RegExpTT.QUEST) {
      builder.advanceLexer();
    }
    else {
      if (RegExpTT.QUANTIFIERS.contains(builder.getTokenType())) {
        builder.error(RegExpBundle.message("error.dangling.metacharacter", builder.getTokenText()));
      }
    }
  }

  /**
   * CLASS            ::= "[" NEGATION DEFLIST "]"
   * NEGATION         ::= "^" | ""
   * DEFLIST          ::= INTERSECTION DEFLIST
   * INTERSECTION     ::= INTERSECTION "&&" CLASSDEF | CLASSDEF
   * CLASSDEF         ::= CLASS | SIMPLE_CLASSDEF | ""
   * SIMPLE_CLASSDEF  ::= CHARACTER | CHARACTER "-" CLASSDEF
   */
  private PsiBuilder.Marker parseClass(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();

    if (builder.getTokenType() == RegExpTT.CARET) {
      builder.advanceLexer();
    }
    parseClassIntersection(builder);

    checkMatches(builder, RegExpTT.CLASS_END, RegExpBundle.message("parse.error.unclosed.character.class"));
    marker.done(RegExpElementTypes.CLASS);
    return marker;
  }

  private void parseClassIntersection(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();

    boolean left = parseClassdef(builder);
    if (RegExpTT.ANDAND != builder.getTokenType()) {
      marker.drop();
      return;
    }
    while (RegExpTT.ANDAND == builder.getTokenType()) {
      builder.advanceLexer();
      final boolean right = parseClassdef(builder);
      if (!left && !right) {
        builder.error(RegExpBundle.message("parse.error.character.class.expected"));
      }
      left = right;
    }
    marker.done(RegExpElementTypes.INTERSECTION);
  }

  private boolean parseClassdef(PsiBuilder builder) {
    int count = 0;
    while (true) {
      final IElementType token = builder.getTokenType();
      if (token == RegExpTT.CLASS_BEGIN) {
        parseClass(builder);
      }
      else if (token == RegExpTT.BRACKET_EXPRESSION_BEGIN) {
        parseBracketExpression(builder);
      }
      else if (token == RegExpTT.MYSQL_CHAR_BEGIN) {
        parseMysqlCharExpression(builder);
      }
      else if (token == RegExpTT.MYSQL_CHAR_EQ_BEGIN) {
        parseMysqlCharEqExpression(builder);
      }
      else if (RegExpTT.CHARACTERS.contains(token) || token == RegExpTT.NAMED_CHARACTER) {
        parseCharacterRange(builder);
      }
      else if (token == RegExpTT.CHAR_CLASS) {
        final PsiBuilder.Marker m = builder.mark();
        builder.advanceLexer();
        m.done(RegExpElementTypes.SIMPLE_CLASS);
      }
      else if (token == RegExpTT.PROPERTY) {
        parseProperty(builder);
      }
      else {
        return count > 0;
      }
      count++;
    }
  }

  private static void parseBracketExpression(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == RegExpTT.CARET) {
      builder.advanceLexer();
    }
    checkMatches(builder, RegExpTT.NAME, RegExpBundle.message("parse.error.posix.character.class.name.expected"));
    checkMatches(builder, RegExpTT.BRACKET_EXPRESSION_END, RegExpBundle.message("parse.error.unclosed.posix.bracket.expression"));
    marker.done(RegExpElementTypes.POSIX_BRACKET_EXPRESSION);
  }

  private static void parseMysqlCharExpression(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == RegExpTT.NAME) {
      builder.advanceLexer();
    }
    else {
      checkMatches(builder, RegExpTT.CHARACTER, RegExpBundle.message("parse.error.character.or.mysql.character.name.expected"));
    }
    checkMatches(builder, RegExpTT.MYSQL_CHAR_END, RegExpBundle.message("parse.error.unclosed.mysql.character.expression"));
    marker.done(RegExpElementTypes.MYSQL_CHAR_EXPRESSION);
  }

  private static void parseMysqlCharEqExpression(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    checkMatches(builder, RegExpTT.CHARACTER, RegExpBundle.message("parse.error.character.expected"));
    checkMatches(builder, RegExpTT.MYSQL_CHAR_EQ_END, RegExpBundle.message("parse.error.unclosed.mysql.character.equivalence.class"));
    marker.done(RegExpElementTypes.MYSQL_CHAR_EQ_EXPRESSION);
  }

  private void parseCharacterRange(PsiBuilder builder) {
    final PsiBuilder.Marker rangeMarker = builder.mark();
    parseCharacter(builder);

    if (builder.getTokenType() == RegExpTT.MINUS) {
      final PsiBuilder.Marker minusMarker = builder.mark();
      builder.advanceLexer();

      final IElementType t = builder.getTokenType();
      if (RegExpTT.CHARACTERS.contains(t) || t == RegExpTT.NAMED_CHARACTER) {
        minusMarker.drop();
        parseCharacter(builder);
        rangeMarker.done(RegExpElementTypes.CHAR_RANGE);
      }
      else {
        if (t == RegExpTT.CLASS_END) { // [a-]
          rangeMarker.drop();
          minusMarker.done(RegExpElementTypes.CHAR);
        }
        else if (t == RegExpTT.CLASS_BEGIN) { // [a-[b]]\
          rangeMarker.drop();
          minusMarker.done(RegExpElementTypes.CHAR);
          parseClassdef(builder);
        }
        else {
          minusMarker.drop();
          builder.error(RegExpBundle.message("parse.error.illegal.character.range"));
          rangeMarker.done(RegExpElementTypes.CHAR_RANGE);
        }
      }
    }
    else {
      rangeMarker.drop();
    }
  }

  /**
   * GROUP  ::= "(" PATTERN ")" | TERM
   * TERM   ::= "." | "$" | "^" | CHAR | CLASS | BACKREF
   */
  @Nullable
  private PsiBuilder.Marker parseGroup(PsiBuilder builder) {
    final IElementType type = builder.getTokenType();

    final PsiBuilder.Marker marker = builder.mark();

    if (RegExpTT.GROUPS.contains(type)) {
      builder.advanceLexer();
      parseGroupEnd(builder);
      marker.done(RegExpElementTypes.GROUP);
    }
    else if (type == RegExpTT.SET_OPTIONS) {
      builder.advanceLexer();

      if (builder.getTokenType() == RegExpTT.OPTIONS_ON) {
        final PsiBuilder.Marker o = builder.mark();
        builder.advanceLexer();
        o.done(RegExpElementTypes.OPTIONS);
      }
      if (builder.getTokenType() == RegExpTT.OPTIONS_OFF) {
        final PsiBuilder.Marker o = builder.mark();
        builder.advanceLexer();
        o.done(RegExpElementTypes.OPTIONS);
      }

      if (builder.getTokenType() == RegExpTT.COLON) {
        builder.advanceLexer();
        parseGroupEnd(builder);
        marker.done(RegExpElementTypes.GROUP);
      }
      else {
        checkMatches(builder, RegExpTT.GROUP_END, RegExpBundle.message("parse.error.unclosed.options.group"));
        marker.done(RegExpElementTypes.SET_OPTIONS);
      }
    }
    else if (RegExpTT.CHARACTERS.contains(type) || type == RegExpTT.NAMED_CHARACTER) {
      marker.drop();
      parseCharacter(builder);
    }
    else if (type == RegExpTT.NUMBER || type == RegExpTT.COMMA) {
      // don't show these as errors
      builder.remapCurrentToken(RegExpTT.CHARACTER);
      builder.advanceLexer();
      marker.done(RegExpElementTypes.CHAR);
    }
    else if (RegExpTT.BOUNDARIES.contains(type)) {
      builder.advanceLexer();
      marker.done(RegExpElementTypes.BOUNDARY);
    }
    else if (type == RegExpTT.BACKREF) {
      builder.advanceLexer();
      marker.done(RegExpElementTypes.BACKREF);
    }
    else if (type == RegExpTT.PYTHON_NAMED_GROUP || type == RegExpTT.RUBY_NAMED_GROUP || type == RegExpTT.RUBY_QUOTED_NAMED_GROUP) {
      builder.advanceLexer();
      checkMatches(builder, RegExpTT.NAME, RegExpBundle.message("parse.error.group.name.expected"));
      checkMatches(builder, type == RegExpTT.RUBY_QUOTED_NAMED_GROUP ? RegExpTT.QUOTE : RegExpTT.GT,
                   RegExpBundle.message("parse.error.unclosed.group.name"));
      parseGroupEnd(builder);
      marker.done(RegExpElementTypes.GROUP);
    }
    else if (type == RegExpTT.PYTHON_NAMED_GROUP_REF || type == RegExpTT.PCRE_RECURSIVE_NAMED_GROUP_REF) {
      parseNamedGroupRef(builder, marker, RegExpTT.GROUP_END);
    }
    else if (type == RegExpTT.RUBY_NAMED_GROUP_REF || type == RegExpTT.RUBY_NAMED_GROUP_CALL) {
      parseNamedGroupRef(builder, marker, RegExpTT.GT);
    }
    else if (type == RegExpTT.RUBY_QUOTED_NAMED_GROUP_REF || type == RegExpTT.RUBY_QUOTED_NAMED_GROUP_CALL) {
      parseNamedGroupRef(builder, marker, RegExpTT.QUOTE);
    }
    else if (type == RegExpTT.CONDITIONAL) {
      builder.advanceLexer();
      parseCondition(builder);
      parseBranch(builder);
      if (builder.getTokenType() == RegExpTT.UNION) {
        builder.advanceLexer();
        parseBranch(builder);
      }
      if (!checkMatches(builder, RegExpTT.GROUP_END, RegExpBundle.message("parse.error.unclosed.group"))) {
        parseGroupEnd(builder);
      }
      marker.done(RegExpElementTypes.CONDITIONAL);
    }
    else if (type == RegExpTT.PROPERTY) {
      marker.drop();
      parseProperty(builder);
    }
    else if (type == RegExpTT.DOT || type == RegExpTT.CHAR_CLASS) {
      builder.advanceLexer();
      marker.done(RegExpElementTypes.SIMPLE_CLASS);
    }
    else if (type == RegExpTT.CLASS_BEGIN) {
      marker.drop();
      return parseClass(builder);
    }
    else {
      marker.drop();
      return null;
    }
    return marker;
  }

  private void parseCondition(PsiBuilder builder) {
    final IElementType type = builder.getTokenType();
    if (RegExpTT.LOOKAROUND_GROUPS.contains(type)) {
      final PsiBuilder.Marker marker = builder.mark();
      builder.advanceLexer();
      parseGroupEnd(builder);
      marker.done(RegExpElementTypes.GROUP);
    }
    else {
      if (RegExpTT.GROUP_BEGIN == type) {
        parseGroupReferenceCondition(builder, RegExpTT.GROUP_END);
      }
      else if (RegExpTT.QUOTED_CONDITION_BEGIN == type) {
        parseGroupReferenceCondition(builder, RegExpTT.QUOTED_CONDITION_END);
      }
      else if (RegExpTT.ANGLE_BRACKET_CONDITION_BEGIN == type) {
        parseGroupReferenceCondition(builder, RegExpTT.ANGLE_BRACKET_CONDITION_END);
      }
    }
  }

  private void parseGroupReferenceCondition(PsiBuilder builder, IElementType endToken) {
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    final IElementType next = builder.getTokenType();
    final Boolean named;
    if (next == RegExpTT.NAME) {
      builder.advanceLexer();
      named = true;
    }
    else if (next == RegExpTT.NUMBER) {
      builder.advanceLexer();
      named = false;
    }
    else {
      named = null;
      builder.error(RegExpBundle.message("parse.error.group.name.or.number.expected"));
      parsePattern(builder);
    }
    checkMatches(builder, endToken, RegExpBundle.message("parse.error.unclosed.group.reference"));
    if (named == Boolean.TRUE) {
      marker.done(RegExpElementTypes.NAMED_GROUP_REF);
    }
    else if (named == Boolean.FALSE) {
      marker.done(RegExpElementTypes.BACKREF);
    }
    else {
      marker.drop();
    }
  }

  private void parseGroupEnd(PsiBuilder builder) {
    parsePattern(builder);
    checkMatches(builder, RegExpTT.GROUP_END, RegExpBundle.message("parse.error.unclosed.group"));
  }

  private static void parseNamedGroupRef(PsiBuilder builder, PsiBuilder.Marker marker, IElementType type) {
    builder.advanceLexer();
    checkMatches(builder, RegExpTT.NAME, RegExpBundle.message("parse.error.group.name.expected"));
    checkMatches(builder, type, RegExpBundle.message("parse.error.unclosed.group.reference"));
    marker.done(RegExpElementTypes.NAMED_GROUP_REF);
  }

  private static boolean isLetter(CharSequence text) {
    if (text == null) return false;
    assert text.length() == 1;
    final char c = text.charAt(0);
    return AsciiUtil.isLetter(c);
  }

  private void parseProperty(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == RegExpTT.CATEGORY_SHORT_HAND) {
      if (!myCapabilities.contains(RegExpCapability.UNICODE_CATEGORY_SHORTHAND)) {
        builder.error(RegExpBundle.message("parse.error.category.shorthand.not.allowed.in.this.regular.expression.dialect"));
      }
      builder.advanceLexer();
    }
    else {
      if (builder.getTokenType() == RegExpTT.CHARACTER && isLetter(builder.getTokenText())) {
        builder.error(myCapabilities.contains(RegExpCapability.UNICODE_CATEGORY_SHORTHAND) ?
                      RegExpBundle.message("parse.error.illegal.category.shorthand") :
                      RegExpBundle.message("parse.error.opening.brace.expected"));
        builder.advanceLexer();
      }
      else if (checkMatches(builder, RegExpTT.LBRACE, myCapabilities.contains(RegExpCapability.UNICODE_CATEGORY_SHORTHAND) ?
                                                      RegExpBundle.message("parse.error.opening.brace.or.category.shorthand.expected") :
                                                      RegExpBundle.message("parse.error.opening.brace.expected"))) {
        if (builder.getTokenType() == RegExpTT.CARET) {
          if (!myCapabilities.contains(RegExpCapability.CARET_NEGATED_PROPERTIES)) {
            builder.error(RegExpBundle.message("parse.error.negating.a.property.not.allowed.in.this.regular.expression.dialect"));
          }
          builder.advanceLexer();
        }
        if (builder.getTokenType() == RegExpTT.NAME) {
          builder.advanceLexer(); //name
          if (myCapabilities.contains(RegExpCapability.PROPERTY_VALUES) && builder.getTokenType() == RegExpTT.EQ) {
            builder.advanceLexer(); //eq
            checkMatches(builder, RegExpTT.NAME, RegExpBundle.message("parse.error.property.value.expected"));
          }
          checkMatches(builder, RegExpTT.RBRACE, RegExpBundle.message("parse.error.unclosed.property"));
        }
        else
        {
          if (builder.getTokenType() == RegExpTT.RBRACE) {
            builder.error(RegExpBundle.message("parse.error.empty.property"));
            builder.advanceLexer();
          }
          else {
            builder.error(RegExpBundle.message("parse.error.property.name.expected"));
          }
          while (PROPERTY_TOKENS.contains(builder.getTokenType())) {
            builder.advanceLexer();
          }
        }
      }
    }
    marker.done(RegExpElementTypes.PROPERTY);
  }

  private static void parseCharacter(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    if (builder.getTokenType() == RegExpTT.NAMED_CHARACTER) {
      builder.advanceLexer();
      checkMatches(builder, RegExpTT.LBRACE, RegExpBundle.message("parse.error.opening.brace.expected"));
      checkMatches(builder, RegExpTT.NAME, RegExpBundle.message("parse.error.unicode.character.name.expected"));
      checkMatches(builder, RegExpTT.RBRACE, RegExpBundle.message("parse.error.closing.brace.expected"));
      marker.done(RegExpElementTypes.NAMED_CHARACTER);
    }
    else if (builder.getTokenType() == RegExpTT.UNICODE_CHAR) {
      final String text1 = builder.getTokenText();
      assert text1 != null;
      final int value1 = RegExpCharImpl.unescapeChar(text1);
      builder.advanceLexer();
      // merge surrogate pairs into single regexp char
      if (!Character.isSupplementaryCodePoint(value1) && Character.isHighSurrogate((char)value1)) {
        final String text2 = builder.getTokenText();
        assert text2 != null;
        final int value2 = RegExpCharImpl.unescapeChar(text2);
        if (!Character.isSupplementaryCodePoint(value2) && Character.isLowSurrogate((char)value2)) {
          builder.advanceLexer();
        }
      }
      marker.done(RegExpElementTypes.CHAR);
    }
    else {
      builder.advanceLexer();
      marker.done(RegExpElementTypes.CHAR);
    }
  }

  private static void patternExpected(PsiBuilder builder) {
    final IElementType token = builder.getTokenType();
    if (token == RegExpTT.GROUP_END || token == RegExpTT.RBRACE || token == RegExpTT.CLASS_END) {
      builder.error(RegExpBundle.message("parse.error.unmatched.closing.bracket", builder.getTokenText()));
    }
    else if (token == RegExpTT.LBRACE) {
      builder.error(RegExpBundle.message("error.dangling.opening.bracket"));
      // try to recover
      builder.advanceLexer();
      while (builder.getTokenType() == RegExpTT.NUMBER || builder.getTokenType() == RegExpTT.COMMA) {
        builder.advanceLexer();
      }
      if (builder.getTokenType() == RegExpTT.RBRACE) {
        builder.advanceLexer();
      }
    }
    else if (RegExpTT.QUANTIFIERS.contains(token)) {
      builder.error(RegExpBundle.message("error.dangling.metacharacter", builder.getTokenText()));
    }
    else {
      builder.error(RegExpBundle.message("parse.error.pattern.expected"));
    }
    builder.advanceLexer();
  }

  protected static boolean checkMatches(final PsiBuilder builder, final IElementType token, @NotNull @NlsContexts.ParsingError String message) {
    if (builder.getTokenType() == token) {
      builder.advanceLexer();
      return true;
    }
    else {
      builder.error(message);
      return false;
    }
  }
}
