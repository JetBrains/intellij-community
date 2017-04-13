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
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class RegExpParser implements PsiParser {
  private static final TokenSet PROPERTY_TOKENS = TokenSet.create(RegExpTT.NUMBER, RegExpTT.COMMA, RegExpTT.NAME, RegExpTT.RBRACE);
  private final EnumSet<RegExpCapability> myCapabilities;

  public RegExpParser(EnumSet<RegExpCapability> capabilities) {
    myCapabilities = capabilities;
  }

  @Override
  @NotNull
  public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    final PsiBuilder.Marker rootMarker = builder.mark();

    parsePattern(builder);

    while (!builder.eof()) {
      patternExpected(builder);
      builder.advanceLexer();
    }

    rootMarker.done(root);
    return builder.getTreeBuilt();
  }


  /**
   * PATTERN ::= BRANCH "|" PATTERN | BRANCH
   */
  private boolean parsePattern(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();

    if (!parseBranch(builder)) {
      marker.drop();
      return false;
    }

    while (builder.getTokenType() == RegExpTT.UNION) {
      builder.advanceLexer();
      if (!parseBranch(builder)) {
        patternExpected(builder);
        break;
      }
    }

    marker.done(RegExpElementTypes.PATTERN);

    return true;
  }

  /**
   * BRANCH  ::= ATOM BRANCH | ""
   */
  private boolean parseBranch(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();

    if (!parseAtom(builder)) {
      final IElementType token = builder.getTokenType();
      if (token == RegExpTT.GROUP_END || token == RegExpTT.UNION || token == null) {
        // empty branches are allowed
        marker.done(RegExpElementTypes.BRANCH);
        return true;
      }
      marker.drop();
      return false;
    }

    //noinspection StatementWithEmptyBody
    while (parseAtom(builder)) {}

    marker.done(RegExpElementTypes.BRANCH);
    return true;
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
          builder.error("Number expected");
        }
      }
      if (builder.getTokenType() == RegExpTT.RBRACE) {
        builder.advanceLexer();
        parseQuantifierType(builder);
        marker.done(RegExpElementTypes.QUANTIFIER);
      }
      else {
        if (!minOmitted) {
          checkMatches(builder, RegExpTT.COMMA, "',' expected");
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
          checkMatches(builder, RegExpTT.RBRACE, "'}' expected");
          parseQuantifierType(builder);
          marker.done(RegExpElementTypes.QUANTIFIER);
        }
        else {
          builder.error("'}' or number expected");
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
        builder.error("Dangling metacharacter");
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

    checkMatches(builder, RegExpTT.CLASS_END, "Unclosed character class");
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
        builder.error("character class expected");
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
    checkMatches(builder, RegExpTT.NAME, "POSIX character class name expected");
    checkMatches(builder, RegExpTT.BRACKET_EXPRESSION_END, "Unclosed POSIX bracket expression");
    marker.done(RegExpElementTypes.POSIX_BRACKET_EXPRESSION);
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
          builder.error("Illegal character range");
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
        checkMatches(builder, RegExpTT.GROUP_END, "Unclosed options group");
        marker.done(RegExpElementTypes.SET_OPTIONS);
      }
    }
    else if (RegExpTT.CHARACTERS.contains(type) || type == RegExpTT.NAMED_CHARACTER) {
      marker.drop();
      parseCharacter(builder);
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
      checkMatches(builder, RegExpTT.NAME, "Group name expected");
      checkMatches(builder, type == RegExpTT.RUBY_QUOTED_NAMED_GROUP ? RegExpTT.QUOTE : RegExpTT.GT, "Unclosed group name");
      parseGroupEnd(builder);
      marker.done(RegExpElementTypes.GROUP);
    }
    else if (type == RegExpTT.PYTHON_NAMED_GROUP_REF) {
      parseNamedGroupRef(builder, marker, RegExpTT.GROUP_END);
    }
    else if (type == RegExpTT.RUBY_NAMED_GROUP_REF || type == RegExpTT.RUBY_NAMED_GROUP_CALL) {
      parseNamedGroupRef(builder, marker, RegExpTT.GT);
    }
    else if (type == RegExpTT.RUBY_QUOTED_NAMED_GROUP_REF || type == RegExpTT.RUBY_QUOTED_NAMED_GROUP_CALL) {
      parseNamedGroupRef(builder, marker, RegExpTT.QUOTE);
    }
    else if (type == RegExpTT.PYTHON_COND_REF) {
      builder.advanceLexer();
      if (builder.getTokenType() == RegExpTT.NAME || builder.getTokenType() == RegExpTT.NUMBER) {
        builder.advanceLexer();
      }
      else {
        builder.error("Group name or number expected");
      }
      checkMatches(builder, RegExpTT.GROUP_END, "Unclosed group reference");
      if (!parseBranch(builder)) {
        patternExpected(builder);
      }
      else {
        if (builder.getTokenType() == RegExpTT.UNION) {
          builder.advanceLexer();
          if (!parseBranch(builder)) {
            patternExpected(builder);
          }
        }
        checkMatches(builder, RegExpTT.GROUP_END, "Unclosed group");
      }
      marker.done(RegExpElementTypes.PY_COND_REF);
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

  private void parseGroupEnd(PsiBuilder builder) {
    if (!parsePattern(builder)) {
      patternExpected(builder);
    }
    else {
      checkMatches(builder, RegExpTT.GROUP_END, "Unclosed group");
    }
  }

  private static void parseNamedGroupRef(PsiBuilder builder, PsiBuilder.Marker marker, IElementType type) {
    builder.advanceLexer();
    checkMatches(builder, RegExpTT.NAME, "Group name expected");
    checkMatches(builder, type, "Unclosed group reference");
    marker.done(RegExpElementTypes.NAMED_GROUP_REF);
  }

  private static boolean isLetter(CharSequence text) {
    assert text.length() == 1;
    final char c = text.charAt(0);
    return AsciiUtil.isLetter(c);
  }

  private void parseProperty(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == RegExpTT.CATEGORY_SHORT_HAND) {
      if (!myCapabilities.contains(RegExpCapability.UNICODE_CATEGORY_SHORTHAND)) {
        builder.error("Category shorthand not allowed in this regular expression dialect");
      }
      builder.advanceLexer();
    }
    else {
      if (builder.getTokenType() == RegExpTT.CHARACTER && isLetter(builder.getTokenText())) {
        builder.error(myCapabilities.contains(RegExpCapability.UNICODE_CATEGORY_SHORTHAND) ?
                      "Illegal category shorthand" :
                      "'{' expected");
        builder.advanceLexer();
      }
      else if (checkMatches(builder, RegExpTT.LBRACE, myCapabilities.contains(RegExpCapability.UNICODE_CATEGORY_SHORTHAND) ?
                                                      "'{' or category shorthand expected" :
                                                      "'{' expected")) {
        if (builder.getTokenType() == RegExpTT.CARET) {
          if (!myCapabilities.contains(RegExpCapability.CARET_NEGATED_PROPERTIES)) {
            builder.error("Negating a property not allowed in this regular expression dialect");
          }
          builder.advanceLexer();
        }
        if (builder.getTokenType() == RegExpTT.NAME) {
          builder.advanceLexer();
          checkMatches(builder, RegExpTT.RBRACE, "Unclosed property");
        }
        else
        {
          if (builder.getTokenType() == RegExpTT.RBRACE) {
            builder.error("Empty property");
            builder.advanceLexer();
          }
          else {
            builder.error("Property name expected");
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
      checkMatches(builder, RegExpTT.LBRACE, "'{' expected");
      checkMatches(builder, RegExpTT.NAME, "Unicode character name expected");
      checkMatches(builder, RegExpTT.RBRACE, "'}' expected");
      marker.done(RegExpElementTypes.NAMED_CHARACTER);
    }
    else {
      builder.advanceLexer();
      marker.done(RegExpElementTypes.CHAR);
    }
  }

  private static void patternExpected(PsiBuilder builder) {
    final IElementType token = builder.getTokenType();
    if (token == RegExpTT.GROUP_END) {
      builder.error("Unmatched closing ')'");
    }
    else if (RegExpTT.QUANTIFIERS.contains(token) || token == RegExpTT.RBRACE || token == RegExpTT.CLASS_END) {
      builder.error("Dangling metacharacter");
    }
    else {
      builder.error("Pattern expected");
    }
  }

  protected static boolean checkMatches(final PsiBuilder builder, final IElementType token, final String message) {
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
