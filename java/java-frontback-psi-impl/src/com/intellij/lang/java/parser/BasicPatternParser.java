// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.AbstractBasicJavaElementTypeFactory;
import com.intellij.psi.impl.source.WhiteSpaceAndCommentSetHolder;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.BasicJavaParserUtil.*;

/**
 * @deprecated Use the new Java syntax library instead.
 *             See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
public class BasicPatternParser {
  private static final TokenSet PATTERN_MODIFIERS = TokenSet.create(JavaTokenType.FINAL_KEYWORD);

  private final BasicJavaParser myParser;
  private final AbstractBasicJavaElementTypeFactory.JavaElementTypeContainer myJavaElementTypeContainer;
  private final WhiteSpaceAndCommentSetHolder myWhiteSpaceAndCommentSetHolder = WhiteSpaceAndCommentSetHolder.INSTANCE;

  public BasicPatternParser(@NotNull BasicJavaParser javaParser) {
    myParser = javaParser;
    myJavaElementTypeContainer = javaParser.getJavaElementTypeFactory().getContainer();
  }

  /**
   * Checks whether given token sequence can be parsed as a pattern.
   * The result of the method makes sense only for places where pattern is expected (case label and instanceof expression).
   */
  @Contract(pure = true)
  public boolean isPattern(final PsiBuilder builder) {
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
      emptyElement(builder, myJavaElementTypeContainer.TYPE);
      builder.advanceLexer();
      done(patternStart, myJavaElementTypeContainer.UNNAMED_PATTERN, builder, myWhiteSpaceAndCommentSetHolder);
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
      myParser.getReferenceParser().parseType(builder, BasicReferenceParser.EAT_LAST_DOT | BasicReferenceParser.WILDCARD);
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
        int flags = BasicReferenceParser.EAT_LAST_DOT | BasicReferenceParser.WILDCARD | BasicReferenceParser.VAR_TYPE;
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
    recordStructure.done(myJavaElementTypeContainer.DECONSTRUCTION_LIST);
  }

  private PsiBuilder.@NotNull Marker parseTypeOrRecordPattern(final PsiBuilder builder, boolean expectVar) {
    PsiBuilder.Marker pattern = builder.mark();
    PsiBuilder.Marker patternVariable = builder.mark();
    Boolean hasNoModifiers = myParser.getDeclarationParser().parseModifierList(builder, PATTERN_MODIFIERS).second;

    int flags = BasicReferenceParser.EAT_LAST_DOT | BasicReferenceParser.WILDCARD;
    if (expectVar) {
      flags |= BasicReferenceParser.VAR_TYPE;
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
        variable.done(myJavaElementTypeContainer.DECONSTRUCTION_PATTERN_VARIABLE);
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
      done(pattern, myJavaElementTypeContainer.DECONSTRUCTION_PATTERN, builder, myWhiteSpaceAndCommentSetHolder);
    }
    else {
      if (hasIdentifier) {
        done(patternVariable, myJavaElementTypeContainer.PATTERN_VARIABLE, builder, myWhiteSpaceAndCommentSetHolder);
      }
      else {
        patternVariable.drop();
      }
      done(pattern, myJavaElementTypeContainer.TYPE_TEST_PATTERN, builder, myWhiteSpaceAndCommentSetHolder);
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
    if(previousIsRecord) return false;
    return true;
  }
}
