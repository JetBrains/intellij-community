// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.OldParserWhiteSpaceAndCommentSetHolder;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.JavaParserUtil.done;
import static com.intellij.lang.java.parser.JavaParserUtil.emptyElement;
import static com.intellij.lang.java.parser.JavaParserUtil.error;
import static com.intellij.lang.java.parser.JavaParserUtil.expectOrError;
import static com.intellij.psi.impl.source.tree.JavaElementType.DECONSTRUCTION_LIST;
import static com.intellij.psi.impl.source.tree.JavaElementType.DECONSTRUCTION_PATTERN;
import static com.intellij.psi.impl.source.tree.JavaElementType.DECONSTRUCTION_PATTERN_VARIABLE;
import static com.intellij.psi.impl.source.tree.JavaElementType.PATTERN_VARIABLE;
import static com.intellij.psi.impl.source.tree.JavaElementType.TYPE;
import static com.intellij.psi.impl.source.tree.JavaElementType.TYPE_TEST_PATTERN;
import static com.intellij.psi.impl.source.tree.JavaElementType.UNNAMED_PATTERN;

/**
 * @deprecated Use the new Java syntax library instead.
 * See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
public class PatternParser {
  private static final TokenSet PATTERN_MODIFIERS = TokenSet.create(JavaTokenType.FINAL_KEYWORD);
  private final JavaParser myParser;
  private final OldParserWhiteSpaceAndCommentSetHolder myWhiteSpaceAndCommentSetHolder = OldParserWhiteSpaceAndCommentSetHolder.INSTANCE;

  public PatternParser(@NotNull JavaParser javaParser) {
    this.myParser = javaParser;
  }

  @Contract(pure = true)
  public boolean isPattern(PsiBuilder builder) {
    PsiBuilder.Marker patternStart = preParsePattern(builder);
    if (patternStart == null) {
      return false;
    }
    patternStart.rollbackTo();
    return true;
  }

  private boolean parseUnnamedPattern(final PsiBuilder builder) {
    PsiBuilder.Marker patternStart = builder.mark();
    if (builder.getTokenType() == JavaTokenType.IDENTIFIER &&
        "_".equals(builder.getTokenText())) {
      emptyElement(builder, TYPE);
      builder.advanceLexer();
      done(patternStart, UNNAMED_PATTERN, builder, myWhiteSpaceAndCommentSetHolder);
      return true;
    }
    patternStart.rollbackTo();
    return false;
  }

  @Nullable("when not pattern")
  PsiBuilder.Marker preParsePattern(final PsiBuilder builder) {
    PsiBuilder.Marker patternStart = builder.mark();
    Boolean hasNoModifier = myParser.getDeclarationParser().parseModifierList(builder, PATTERN_MODIFIERS).second;
    PsiBuilder.Marker type =
      myParser.getReferenceParser().parseType(builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD);
    boolean isPattern = type != null && (builder.getTokenType() == JavaTokenType.IDENTIFIER ||
                                         (builder.getTokenType() == JavaTokenType.LPARENTH && hasNoModifier));
    if (!isPattern) {
      patternStart.rollbackTo();
      return null;
    }
    return patternStart;
  }

  /**
   * Must be called only if isPattern returned true
   */
  public PsiBuilder.@NotNull Marker parsePattern(final PsiBuilder builder) {
    return parsePattern(builder, false);
  }

  private PsiBuilder.@NotNull Marker parsePattern(final PsiBuilder builder, boolean expectVar) {
    return parsePrimaryPattern(builder, expectVar);
  }

  PsiBuilder.@NotNull Marker parsePrimaryPattern(final PsiBuilder builder, boolean expectVar) {
    return parseTypeOrRecordPattern(builder, expectVar);
  }

  private void parseRecordStructurePattern(final PsiBuilder builder) {
    PsiBuilder.Marker recordStructure = builder.mark();
    boolean hasLparen = expect(builder, JavaTokenType.LPARENTH);
    assert hasLparen;

    boolean isFirst = true;
    while (builder.getTokenType() != JavaTokenType.RPARENTH) {
      if (!isFirst) {
        expectOrError(builder, JavaTokenType.COMMA, "expected.comma");
      }

      if (builder.getTokenType() == null) {
        break;
      }

      if (isPattern(builder)) {
        parsePattern(builder, true);
        isFirst = false;
      }
      else if (parseUnnamedPattern(builder)) {
        isFirst = false;
      }
      else {
        int flags = ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD | ReferenceParser.VAR_TYPE;
        myParser.getReferenceParser().parseType(builder, flags);
        error(builder, JavaPsiBundle.message("expected.pattern"));
        if (builder.getTokenType() == JavaTokenType.RPARENTH) {
          break;
        }
        builder.advanceLexer();
      }
    }
    if (!expect(builder, JavaTokenType.RPARENTH)) {
      builder.error(JavaPsiBundle.message("expected.rparen"));
    }
    recordStructure.done(DECONSTRUCTION_LIST);
  }

  private PsiBuilder.@NotNull Marker parseTypeOrRecordPattern(final PsiBuilder builder, boolean expectVar) {
    PsiBuilder.Marker pattern = builder.mark();
    PsiBuilder.Marker patternVariable = builder.mark();
    Boolean hasNoModifiers = myParser.getDeclarationParser().parseModifierList(builder, PATTERN_MODIFIERS).second;

    int flags = ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD;
    if (expectVar) {
      flags |= ReferenceParser.VAR_TYPE;
    }
    PsiBuilder.Marker type = myParser.getReferenceParser().parseType(builder, flags);
    assert type != null; // guarded by isPattern
    boolean isRecord = false;
    if (builder.getTokenType() == JavaTokenType.LPARENTH && hasNoModifiers) {
      parseRecordStructurePattern(builder);
      isRecord = true;
    }

    final boolean hasIdentifier;
    if (builder.getTokenType() == JavaTokenType.IDENTIFIER &&
        (!JavaKeywords.WHEN.equals(builder.getTokenText()) || isWhenAsIdentifier(isRecord))) {
      // pattern variable after the record structure pattern
      if (isRecord) {
        PsiBuilder.Marker variable = builder.mark();
        builder.advanceLexer();
        variable.done(DECONSTRUCTION_PATTERN_VARIABLE);
      }
      else {
        builder.advanceLexer();
      }
      hasIdentifier = true;
    }
    else {
      hasIdentifier = false;
    }

    if (isRecord) {
      patternVariable.drop();
      done(pattern, DECONSTRUCTION_PATTERN, builder, myWhiteSpaceAndCommentSetHolder);
    }
    else {
      if (hasIdentifier) {
        done(patternVariable, PATTERN_VARIABLE, builder, myWhiteSpaceAndCommentSetHolder);
      }
      else {
        patternVariable.drop();
      }
      done(pattern, TYPE_TEST_PATTERN, builder, myWhiteSpaceAndCommentSetHolder);
    }
    return pattern;
  }

  // There may be valid code samples:
  // Rec(int i) when  when     when.foo() -> {} //now it is unsupported, let's skip it
  //            ^name ^keyword ^guard expr
  //case When when -> {}
  //            ^name
  //case When(when) when              when ->{}
  //                  ^keyword         ^guard expr
  private static boolean isWhenAsIdentifier(boolean previousIsRecord) {
    if (previousIsRecord) return false;
    return true;
  }
}
