// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.parser

import com.intellij.java.syntax.JavaSyntaxBundle.message
import com.intellij.java.syntax.element.JavaSyntaxElementType
import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.java.syntax.element.SyntaxElementTypes
import com.intellij.java.syntax.element.WhiteSpaceAndCommentSetHolder
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.expect

class ModuleParser(private val myParser: JavaParser) {
  fun parse(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    val module = builder.mark()

    val firstAnnotation = myParser.declarationParser.parseAnnotations(builder)

    val type = builder.tokenType
    var text = if (type === JavaSyntaxTokenType.IDENTIFIER) builder.tokenText else null
    if (!(JavaKeywords.OPEN == text || JavaKeywords.MODULE == text)) {
      module.rollbackTo()
      return null
    }

    val modifierList = firstAnnotation?.precede() ?: builder.mark()
    if (JavaKeywords.OPEN == text) {
      mapAndAdvance(builder, JavaSyntaxTokenType.OPEN_KEYWORD)
      text = builder.tokenText
    }
    JavaParserUtil.done(modifierList, JavaSyntaxElementType.MODIFIER_LIST, myParser.languageLevel, WhiteSpaceAndCommentSetHolder)

    if (JavaKeywords.MODULE == text) {
      mapAndAdvance(builder, JavaSyntaxTokenType.MODULE_KEYWORD)
    }
    else {
      module.drop()
      parseExtras(builder, message("expected.module.declaration"))
      return module
    }

    if (parseName(builder) == null) {
      module.drop()
      if (builder.tokenType != null) {
        parseExtras(builder, message("expected.module.declaration"))
      }
      else {
        JavaParserUtil.error(builder, message("expected.identifier"))
      }
      return module
    }

    if (!builder.expect(JavaSyntaxTokenType.LBRACE)) {
      if (builder.tokenType != null) {
        parseExtras(builder, message("expected.module.declaration"))
      }
      else {
        JavaParserUtil.error(builder, message("expected.lbrace"))
      }
    }
    else {
      parseModuleContent(builder)
    }

    JavaParserUtil.done(module, JavaSyntaxElementType.MODULE, myParser.languageLevel, WhiteSpaceAndCommentSetHolder)

    if (builder.tokenType != null) {
      parseExtras(builder, message("unexpected.tokens"))
    }

    return module
  }

  fun parseName(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    val nameElement = builder.mark()
    var empty = true

    var idExpected = true
    while (true) {
      val t = builder.tokenType
      if (t === JavaSyntaxTokenType.IDENTIFIER) {
        if (!idExpected) JavaParserUtil.error(builder, message("expected.dot"))
        idExpected = false
      }
      else if (t === JavaSyntaxTokenType.DOT) {
        if (idExpected) JavaParserUtil.error(builder, message("expected.identifier"))
        idExpected = true
      }
      else break
      builder.advanceLexer()
      empty = false
    }

    if (!empty) {
      if (idExpected) JavaParserUtil.error(builder, message("expected.identifier"))
      nameElement.done(JavaSyntaxElementType.MODULE_REFERENCE)
      return nameElement
    }
    else {
      nameElement.drop()
      return null
    }
  }

  private fun parseModuleContent(builder: SyntaxTreeBuilder) {
    var token: SyntaxElementType?
    var invalid: SyntaxTreeBuilder.Marker? = null

    while ((builder.tokenType.also { token = it }) != null) {
      if (token === JavaSyntaxTokenType.RBRACE) {
        break
      }

      if (token === JavaSyntaxTokenType.SEMICOLON) {
        if (invalid != null) {
          invalid.error(message("expected.module.statement"))
          invalid = null
        }
        builder.advanceLexer()
        continue
      }

      val statement = parseStatement(builder)
      if (statement == null) {
        if (invalid == null) invalid = builder.mark()
        builder.advanceLexer()
      }
      else if (invalid != null) {
        invalid.errorBefore(message("expected.module.statement"), statement)
        invalid = null
      }
    }

    invalid?.error(message("expected.module.statement"))

    if (!builder.expect(JavaSyntaxTokenType.RBRACE) && invalid == null) {
      JavaParserUtil.error(builder, message("expected.rbrace"))
    }
  }

  private fun parseStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    val kw = builder.tokenText
    if (JavaKeywords.REQUIRES == kw) return parseRequiresStatement(builder)
    if (JavaKeywords.EXPORTS == kw) return parseExportsStatement(builder)
    if (JavaKeywords.OPENS == kw) return parseOpensStatement(builder)
    if (JavaKeywords.USES == kw) return parseUsesStatement(builder)
    if (JavaKeywords.PROVIDES == kw) return parseProvidesStatement(builder)
    return null
  }

  private fun parseRequiresStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    mapAndAdvance(builder, JavaSyntaxTokenType.REQUIRES_KEYWORD)

    val modifierList = builder.mark()
    while (true) {
      if (builder.expect(SyntaxElementTypes.MODIFIER_BIT_SET)) continue
      if (builder.tokenType === JavaSyntaxTokenType.IDENTIFIER && JavaKeywords.TRANSITIVE == builder.tokenText) {
        mapAndAdvance(builder, JavaSyntaxTokenType.TRANSITIVE_KEYWORD)
        continue
      }
      break
    }
    JavaParserUtil.done(modifierList, JavaSyntaxElementType.MODIFIER_LIST, myParser.languageLevel, WhiteSpaceAndCommentSetHolder)

    if (parseNameRef(builder) != null) {
      JavaParserUtil.semicolon(builder)
    }
    else {
      builder.expect(JavaSyntaxTokenType.SEMICOLON)
    }

    statement.done(JavaSyntaxElementType.REQUIRES_STATEMENT)
    return statement
  }

  private fun parseExportsStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    mapAndAdvance(builder, JavaSyntaxTokenType.EXPORTS_KEYWORD)
    return parsePackageStatement(builder, statement, JavaSyntaxElementType.EXPORTS_STATEMENT)
  }

  private fun parseOpensStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    mapAndAdvance(builder, JavaSyntaxTokenType.OPENS_KEYWORD)
    return parsePackageStatement(builder, statement, JavaSyntaxElementType.OPENS_STATEMENT)
  }

  private fun parsePackageStatement(
    builder: SyntaxTreeBuilder,
    statement: SyntaxTreeBuilder.Marker,
    type: SyntaxElementType,
  ): SyntaxTreeBuilder.Marker {
    var hasError = false

    if (parseClassOrPackageRef(builder) != null) {
      if (JavaKeywords.TO == builder.tokenText) {
        mapAndAdvance(builder, JavaSyntaxTokenType.TO_KEYWORD)

        while (true) {
          val ref = parseNameRef(builder)
          if (!builder.expect(JavaSyntaxTokenType.COMMA)) {
            if (ref == null) hasError = true
            break
          }
        }
      }
    }
    else {
      JavaParserUtil.error(builder, message("expected.package.reference"))
      hasError = true
    }

    if (!hasError) {
      JavaParserUtil.semicolon(builder)
    }
    else {
      builder.expect(JavaSyntaxTokenType.SEMICOLON)
    }

    statement.done(type)
    return statement
  }

  private fun parseUsesStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    mapAndAdvance(builder, JavaSyntaxTokenType.USES_KEYWORD)

    if (parseClassOrPackageRef(builder) != null) {
      JavaParserUtil.semicolon(builder)
    }
    else {
      JavaParserUtil.error(builder, message("expected.class.reference"))
      builder.expect(JavaSyntaxTokenType.SEMICOLON)
    }

    statement.done(JavaSyntaxElementType.USES_STATEMENT)
    return statement
  }

  private fun parseProvidesStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    var hasError = false
    mapAndAdvance(builder, JavaSyntaxTokenType.PROVIDES_KEYWORD)

    if (parseClassOrPackageRef(builder) == null) {
      JavaParserUtil.error(builder, message("expected.class.reference"))
      hasError = true
    }

    if (JavaKeywords.WITH == builder.tokenText) {
      builder.remapCurrentToken(JavaSyntaxTokenType.WITH_KEYWORD)
      hasError = myParser.referenceParser.parseReferenceList(builder, JavaSyntaxTokenType.WITH_KEYWORD,
                                                             JavaSyntaxElementType.PROVIDES_WITH_LIST, JavaSyntaxTokenType.COMMA)
    }
    else if (!hasError) {
      val next = builder.tokenType
      if (next === JavaSyntaxTokenType.IDENTIFIER && !STATEMENT_KEYWORDS.contains(builder.tokenText)) {
        val marker = builder.mark()
        builder.advanceLexer()
        marker.error(message("expected.with"))
      }
      else {
        JavaParserUtil.error(builder, message("expected.with"))
      }
      hasError = true
    }

    if (!hasError) {
      JavaParserUtil.semicolon(builder)
    }
    else {
      builder.expect(JavaSyntaxTokenType.SEMICOLON)
    }

    statement.done(JavaSyntaxElementType.PROVIDES_STATEMENT)
    return statement
  }

  private fun parseNameRef(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    val name = parseName(builder)
    if (name == null) {
      JavaParserUtil.error(builder, message("expected.identifier"))
    }
    return name
  }

  private fun parseClassOrPackageRef(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    return myParser.referenceParser.parseJavaCodeReference(builder, true, false, false, false)
  }

  private fun mapAndAdvance(builder: SyntaxTreeBuilder, keyword: SyntaxElementType) {
    builder.remapCurrentToken(keyword)
    builder.advanceLexer()
  }

  private fun parseExtras(builder: SyntaxTreeBuilder, message: @NlsContexts.ParsingError String) {
    val extras = builder.mark()
    while (builder.tokenType != null) builder.advanceLexer()
    extras.error(message)
  }
}

private val STATEMENT_KEYWORDS: Set<String> = hashSetOf(JavaKeywords.REQUIRES, JavaKeywords.EXPORTS, JavaKeywords.USES, JavaKeywords.PROVIDES)
