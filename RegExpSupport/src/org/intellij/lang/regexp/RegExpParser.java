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
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"RedundantIfStatement"})
public class RegExpParser implements PsiParser {
  private boolean myAllowDanglingMetacharacters;
  private boolean myAllowOmitNumbersInQuantifiers;

  public void setAllowDanglingMetacharacters(boolean allowDanglingMetacharacters) {
    myAllowDanglingMetacharacters = allowDanglingMetacharacters;
  }

  public void setAllowOmitNumbersInQuantifiers(boolean allowOmitNumbersInQuantifiers) {
    myAllowOmitNumbersInQuantifiers = allowOmitNumbersInQuantifiers;
  }

  @NotNull
  public ASTNode parse(IElementType root, PsiBuilder builder) {
//        builder.setDebugMode(true);
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
        // TODO: no test coverage
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
  @SuppressWarnings({"StatementWithEmptyBody"})
  private boolean parseBranch(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();

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

    for (; parseAtom(builder);) ;

    marker.done(RegExpElementTypes.BRANCH);
    return true;
  }

  /**
   * ATOM        ::= CLOSURE | GROUP
   * CLOSURE     ::= GROUP QUANTIFIER
   */
  private boolean parseAtom(PsiBuilder builder) {
    PsiBuilder.Marker marker = parseGroup(builder);

    if (marker == null) {
      return false;
    }
    marker = marker.precede();

    if (parseQuantifier(builder)) {
      marker.done(RegExpElementTypes.CLOSURE);
    }
    else {
      marker.drop();
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
      boolean minOmitted = false;
      if (builder.getTokenType() == RegExpTT.COMMA && myAllowOmitNumbersInQuantifiers) {
        minOmitted = true;
        builder.advanceLexer();
      }
      else if (builder.getTokenType() != RegExpTT.NUMBER && myAllowDanglingMetacharacters) {
        marker.done(RegExpTT.CHARACTER);
        return true;
      }
      else {
        checkMatches(builder, RegExpTT.NUMBER, "Number expected");
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
          builder.advanceLexer();
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
    if (builder.getTokenType() == RegExpTT.PLUS) {
      builder.advanceLexer();
    }
    else if (builder.getTokenType() == RegExpTT.QUEST) {
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

    // DEFLIST
    if (parseClassIntersection(builder)) {
      while (RegExpTT.CHARACTERS2.contains(builder.getTokenType()) ||
             builder.getTokenType() == RegExpTT.CLASS_BEGIN ||
             builder.getTokenType() == RegExpTT.PROPERTY) {
        parseClassIntersection(builder);
      }
    }

    checkMatches(builder, RegExpTT.CLASS_END, "Unclosed character class");
    marker.done(RegExpElementTypes.CLASS);
    return marker;
  }

  private boolean parseClassIntersection(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();

    if (!parseClassdef(builder, false)) {
      marker.drop();
      return false;
    }
    while (RegExpTT.ANDAND == builder.getTokenType()) {
      builder.advanceLexer();
      parseClassdef(builder, true);
      marker.done(RegExpElementTypes.INTERSECTION);
      marker = marker.precede();
    }

    marker.drop();
    return true;
  }

  private boolean parseClassdef(PsiBuilder builder, boolean mayBeEmpty) {
    final IElementType token = builder.getTokenType();
    if (token == RegExpTT.CLASS_BEGIN) {
      parseClass(builder);
    }
    else if (RegExpTT.CHARACTERS2.contains(token)) {
      parseSimpleClassdef(builder);
    }
    else if (token == RegExpTT.PROPERTY) {
      parseProperty(builder);
    }
    else if (mayBeEmpty) {
      // TODO: no test coverage
      return true;
    }
    else {
      return false;
    }
    return true;
  }

  private void parseSimpleClassdef(PsiBuilder builder) {
    assert RegExpTT.CHARACTERS2.contains(builder.getTokenType());

    final PsiBuilder.Marker marker = builder.mark();
    makeChar(builder);

    IElementType t = builder.getTokenType();
    if (t == RegExpTT.MINUS) {
      final PsiBuilder.Marker m = builder.mark();
      builder.advanceLexer();

      t = builder.getTokenType();
      if (RegExpTT.CHARACTERS2.contains(t)) {
        m.drop();
        makeChar(builder);
        marker.done(RegExpElementTypes.CHAR_RANGE);
      }
      else {
        marker.drop();
        m.done(t == RegExpTT.CHAR_CLASS ? RegExpElementTypes.SIMPLE_CLASS : RegExpElementTypes.CHAR);

        if (t == RegExpTT.CLASS_END) { // [a-]
          return;
        }
        else if (t == RegExpTT.CLASS_BEGIN) { // [a-[b]]
          if (parseClassdef(builder, false)) {
            return;
          }
        }
        builder.error("Illegal character range");
      }
    }
    else {
      marker.drop();
    }
  }

  private static void makeChar(PsiBuilder builder) {
    final IElementType t = builder.getTokenType();
    PsiBuilder.Marker m = builder.mark();
    builder.advanceLexer();
    m.done(t == RegExpTT.CHAR_CLASS ? RegExpElementTypes.SIMPLE_CLASS : RegExpElementTypes.CHAR);
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
      if (!parsePattern(builder)) {
        patternExpected(builder);
      }
      else {
        checkMatches(builder, RegExpTT.GROUP_END, "Unclosed group");
      }
      marker.done(RegExpElementTypes.GROUP);
    }
    else if (type == RegExpTT.SET_OPTIONS) {
      builder.advanceLexer();

      final PsiBuilder.Marker o = builder.mark();
      if (builder.getTokenType() == RegExpTT.OPTIONS_ON) {
        builder.advanceLexer();
      }
      if (builder.getTokenType() == RegExpTT.OPTIONS_OFF) {
        builder.advanceLexer();
      }
      o.done(RegExpElementTypes.OPTIONS);

      if (builder.getTokenType() == RegExpTT.COLON) {
        builder.advanceLexer();
        if (!parsePattern(builder)) {
          // TODO: no test coverage
          patternExpected(builder);
        }
        else {
          checkMatches(builder, RegExpTT.GROUP_END, "Unclosed group");
        }
        marker.done(RegExpElementTypes.GROUP);
      }
      else {
        checkMatches(builder, RegExpTT.GROUP_END, "Unclosed options group");
        marker.done(RegExpElementTypes.SET_OPTIONS);
      }
    }
    else if (type == StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN) {
      builder.error("Illegal/unsupported escape sequence");
      builder.advanceLexer();
      marker.done(RegExpElementTypes.CHAR);
    }
    else if (RegExpTT.CHARACTERS.contains(type)) {
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
      checkMatches(builder, RegExpTT.NAME, "Group name expected");
      checkMatches(builder, type == RegExpTT.RUBY_QUOTED_NAMED_GROUP ? RegExpTT.QUOTE : RegExpTT.GT, "Unclosed group name");
      if (!parsePattern(builder)) {
        patternExpected(builder);
      }
      else {
        checkMatches(builder, RegExpTT.GROUP_END, "Unclosed group");
      }
      marker.done(RegExpElementTypes.GROUP);
    }
    else if (type == RegExpTT.PYTHON_NAMED_GROUP_REF) {
      builder.advanceLexer();
      checkMatches(builder, RegExpTT.NAME, "Group name expected");
      checkMatches(builder, RegExpTT.GROUP_END, "Unclosed group reference");
      marker.done(RegExpElementTypes.PY_NAMED_GROUP_REF);
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
      parseProperty(builder);
      marker.done(RegExpElementTypes.PROPERTY);
    }
    else if (RegExpTT.SIMPLE_CLASSES.contains(type)) {
      builder.advanceLexer();
      marker.done(RegExpElementTypes.SIMPLE_CLASS);
    }
    else if (type == RegExpTT.CLASS_BEGIN) {
      marker.drop();
      return parseClass(builder);
    }
    else if (type == RegExpTT.LBRACE && myAllowDanglingMetacharacters) {
      builder.advanceLexer();
      marker.done(RegExpElementTypes.CHAR);
    }
    else {
      marker.drop();
      return null;
    }
    return marker;
  }

  private static void parseProperty(PsiBuilder builder) {
    checkMatches(builder, RegExpTT.PROPERTY, "'\\p' expected");

    checkMatches(builder, RegExpTT.LBRACE, "Character category expected");
    if (builder.getTokenType() == RegExpTT.NAME) {
      builder.advanceLexer();
    }
    else if (builder.getTokenType() == RegExpTT.RBRACE) {
      builder.error("Empty character family");
    }
    else {
      builder.error("Character family name expected");
      builder.advanceLexer();
    }
    checkMatches(builder, RegExpTT.RBRACE, "Unclosed character family");
  }

  private static void patternExpected(PsiBuilder builder) {
    final IElementType token = builder.getTokenType();
    if (token == RegExpTT.GROUP_END) {
      builder.error("Unmatched closing ')'");
    }
    else if (RegExpTT.QUANTIFIERS.contains(token)) {
      builder.error("Dangling metacharacter");
    }
    else {
      builder.error("Pattern expected");
    }
  }

  protected static void checkMatches(final PsiBuilder builder, final IElementType token, final String message) {
    if (builder.getTokenType() == token) {
      builder.advanceLexer();
    }
    else {
      builder.error(message);
    }
  }
}
