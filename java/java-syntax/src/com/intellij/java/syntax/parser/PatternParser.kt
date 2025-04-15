// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.parser

import com.intellij.java.syntax.JavaSyntaxBundle.message
import com.intellij.java.syntax.element.JavaSyntaxElementType
import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.java.syntax.element.WhiteSpaceAndCommentSetHolder
import com.intellij.java.syntax.parser.JavaParserUtil.done
import com.intellij.java.syntax.parser.JavaParserUtil.emptyElement
import com.intellij.java.syntax.parser.JavaParserUtil.error
import com.intellij.java.syntax.parser.JavaParserUtil.expectOrError
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.expect
import org.jetbrains.annotations.Contract

class PatternParser(private val myParser: JavaParser) {
  private val myWhiteSpaceAndCommentSetHolder = WhiteSpaceAndCommentSetHolder

  /**
   * Checks whether given token sequence can be parsed as a pattern.
   * The result of the method makes sense only for places where pattern is expected (case label and instanceof expression).
   */
  @Contract(pure = true)
  fun isPattern(builder: SyntaxTreeBuilder): Boolean {
    val patternStart = preParsePattern(builder) ?: return false
    patternStart.rollbackTo()
    return true
  }

  private fun parseUnnamedPattern(builder: SyntaxTreeBuilder): Boolean {
    val patternStart = builder.mark()
    if (builder.tokenType === JavaSyntaxTokenType.IDENTIFIER && "_" == builder.tokenText) {
      emptyElement(builder, JavaSyntaxElementType.TYPE)
      builder.advanceLexer()
      done(patternStart, JavaSyntaxElementType.UNNAMED_PATTERN, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
      return true
    }
    patternStart.rollbackTo()
    return false
  }


  fun preParsePattern(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    val patternStart = builder.mark()
    val hasNoModifier = myParser.declarationParser.parseModifierList(builder, PATTERN_MODIFIERS).second
    val type = myParser.referenceParser.parseType(builder, ReferenceParser.EAT_LAST_DOT or ReferenceParser.WILDCARD)
    val isPattern = type != null &&
                    (builder.tokenType === JavaSyntaxTokenType.IDENTIFIER || (builder.tokenType === JavaSyntaxTokenType.LPARENTH && hasNoModifier))
    if (!isPattern) {
      patternStart.rollbackTo()
      return null
    }
    return patternStart
  }

  /**
   * Must be called only if isPattern returned true
   */
  fun parsePattern(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    return parsePattern(builder, false)
  }

  private fun parsePattern(builder: SyntaxTreeBuilder, expectVar: Boolean): SyntaxTreeBuilder.Marker {
    return parsePrimaryPattern(builder, expectVar)
  }

  fun parsePrimaryPattern(builder: SyntaxTreeBuilder, expectVar: Boolean): SyntaxTreeBuilder.Marker {
    return parseTypeOrRecordPattern(builder, expectVar)
  }

  private fun parseRecordStructurePattern(builder: SyntaxTreeBuilder) {
    val recordStructure = builder.mark()
    val hasLparen = builder.expect(JavaSyntaxTokenType.LPARENTH)
    require(hasLparen)

    var isFirst = true
    while (builder.tokenType !== JavaSyntaxTokenType.RPARENTH) {
      if (!isFirst) {
        expectOrError(builder, JavaSyntaxTokenType.COMMA, "expected.comma")
      }

      if (builder.tokenType == null) {
        break
      }

      if (isPattern(builder)) {
        parsePattern(builder, true)
        isFirst = false
      }
      else if (parseUnnamedPattern(builder)) {
        isFirst = false
      }
      else {
        val flags = ReferenceParser.EAT_LAST_DOT or ReferenceParser.WILDCARD or ReferenceParser.VAR_TYPE
        myParser.referenceParser.parseType(builder, flags)
        error(builder, message("expected.pattern"))
        if (builder.tokenType === JavaSyntaxTokenType.RPARENTH) {
          break
        }
        builder.advanceLexer()
      }
    }
    if (!builder.expect(JavaSyntaxTokenType.RPARENTH)) {
      builder.error(message("expected.rparen"))
    }
    recordStructure.done(JavaSyntaxElementType.DECONSTRUCTION_LIST)
  }

  private fun parseTypeOrRecordPattern(builder: SyntaxTreeBuilder, expectVar: Boolean): SyntaxTreeBuilder.Marker {
    val pattern = builder.mark()
    val patternVariable = builder.mark()
    val hasNoModifiers = myParser.declarationParser.parseModifierList(builder, PATTERN_MODIFIERS).second

    var flags = ReferenceParser.EAT_LAST_DOT or ReferenceParser.WILDCARD
    if (expectVar) {
      flags = flags or ReferenceParser.VAR_TYPE
    }
    checkNotNull(myParser.referenceParser.parseType(builder, flags))
    var isRecord = false
    if (builder.tokenType === JavaSyntaxTokenType.LPARENTH && hasNoModifiers) {
      parseRecordStructurePattern(builder)
      isRecord = true
    }

    val hasIdentifier: Boolean
    if (builder.tokenType === JavaSyntaxTokenType.IDENTIFIER &&
        (JavaKeywords.WHEN != builder.tokenText || isWhenAsIdentifier(isRecord))
    ) {
      // pattern variable after the record structure pattern
      if (isRecord) {
        val variable = builder.mark()
        builder.advanceLexer()
        variable.done(JavaSyntaxElementType.DECONSTRUCTION_PATTERN_VARIABLE)
      }
      else {
        builder.advanceLexer()
      }
      hasIdentifier = true
    }
    else {
      hasIdentifier = false
    }

    if (isRecord) {
      patternVariable.drop()
      done(pattern, JavaSyntaxElementType.DECONSTRUCTION_PATTERN, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    }
    else {
      if (hasIdentifier) {
        done(patternVariable, JavaSyntaxElementType.PATTERN_VARIABLE, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
      }
      else {
        patternVariable.drop()
      }
      done(pattern, JavaSyntaxElementType.TYPE_TEST_PATTERN, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    }
    return pattern
  }

  // There may be valid code samples:
  // Rec(int i) when  when     when.foo() -> {} //now it is unsupported, let's skip it
  //            ^name ^keyword ^guard expr
  //case When when -> {}
  //            ^name
  //case When(when) when              when ->{}
  //                  ^keyword         ^guard expr
  private fun isWhenAsIdentifier(previousIsRecord: Boolean): Boolean {
    return !previousIsRecord
  }
}

private val PATTERN_MODIFIERS: SyntaxElementTypeSet = syntaxElementTypeSetOf(JavaSyntaxTokenType.FINAL_KEYWORD)