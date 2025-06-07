// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.parser

import com.intellij.java.syntax.JavaSyntaxBundle
import com.intellij.java.syntax.JavaSyntaxBundle.message
import com.intellij.java.syntax.element.JavaSyntaxElementType
import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.java.syntax.element.SyntaxElementTypes.CLASS_KEYWORD_BIT_SET
import com.intellij.java.syntax.element.SyntaxElementTypes.KEYWORD_BIT_SET
import com.intellij.java.syntax.element.SyntaxElementTypes.MODIFIER_BIT_SET
import com.intellij.java.syntax.element.SyntaxElementTypes.PRIMITIVE_TYPE_BIT_SET
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.advance
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.expect
import com.intellij.pom.java.JavaFeature
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.text.CharArrayUtilKmp.shiftBackward
import com.intellij.util.text.CharArrayUtilKmp.shiftBackwardUntil
import com.intellij.util.text.CharArrayUtilKmp.shiftForward
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import kotlin.jvm.JvmOverloads

@ApiStatus.Experimental
open class DeclarationParser(private val myParser: JavaParser) {
  enum class Context {
    FILE, CLASS, CODE_BLOCK, ANNOTATION_INTERFACE, JSHELL
  }

  private val languageLevel: LanguageLevel
    get() = myParser.languageLevel

  fun parseClassBodyWithBraces(builder: SyntaxTreeBuilder, isAnnotation: Boolean, isEnum: Boolean) {
    require(builder.tokenType === JavaSyntaxTokenType.LBRACE) { builder.tokenType ?: "<null>" }
    builder.advanceLexer()

    val builderWrapper = JavaParserUtil.braceMatchingBuilder(builder)
    if (isEnum) {
      parseEnumConstants(builderWrapper)
    }
    parseClassBodyDeclarations(builderWrapper, isAnnotation)

    JavaParserUtil.expectOrError(builder, JavaSyntaxTokenType.RBRACE, "expected.rbrace")
  }

  private fun parseClassFromKeyword(
    builder: SyntaxTreeBuilder,
    declaration: SyntaxTreeBuilder.Marker,
    isAnnotation: Boolean,
    context: Context?
  ): SyntaxTreeBuilder.Marker? {
    var keywordTokenType = builder.tokenType
    val isRecord = isRecordToken(builder, keywordTokenType)
    if (isRecord) {
      builder.remapCurrentToken(JavaSyntaxTokenType.RECORD_KEYWORD)
      if (builder.lookAhead(1) !== JavaSyntaxTokenType.IDENTIFIER) {
        builder.advanceLexer()
        JavaParserUtil.error(builder, message("expected.identifier"))
        declaration.drop()
        return null
      }
      val afterIdent = builder.lookAhead(2)
      // No parser recovery for local records without < or ( to support light stubs
      // (look at com.intellij.psi.impl.source.JavaLightStubBuilder.CodeBlockVisitor.visit)
      if (context === Context.CODE_BLOCK && afterIdent !== JavaSyntaxTokenType.LPARENTH && afterIdent !== JavaSyntaxTokenType.LT) {
        // skipping record kw and identifier
        builder.advance(2)
        JavaParserUtil.error(builder, message("expected.lt.or.lparen"))
        declaration.drop()
        return null
      }
      keywordTokenType = JavaSyntaxTokenType.RECORD_KEYWORD
    }
    require(CLASS_KEYWORD_BIT_SET.contains(keywordTokenType)) { keywordTokenType ?: "<null>" }
    builder.advanceLexer()
    val isEnum = (keywordTokenType === JavaSyntaxTokenType.ENUM_KEYWORD)

    if (!builder.expect(JavaSyntaxTokenType.IDENTIFIER)) {
      JavaParserUtil.error(builder, message("expected.identifier"))
      declaration.drop()
      return null
    }

    val refParser = myParser.referenceParser
    refParser.parseTypeParameters(builder)

    if (builder.tokenType === JavaSyntaxTokenType.LPARENTH) {
      parseElementList(builder, ListType.RECORD_COMPONENTS)
    }

    refParser.parseReferenceList(builder, JavaSyntaxTokenType.EXTENDS_KEYWORD, JavaSyntaxElementType.EXTENDS_LIST, JavaSyntaxTokenType.COMMA)
    refParser.parseReferenceList(builder, JavaSyntaxTokenType.IMPLEMENTS_KEYWORD, JavaSyntaxElementType.IMPLEMENTS_LIST, JavaSyntaxTokenType.COMMA)

    if (builder.tokenType === JavaSyntaxTokenType.IDENTIFIER && JavaKeywords.PERMITS == builder.tokenText) {
      builder.remapCurrentToken(JavaSyntaxTokenType.PERMITS_KEYWORD)
    }
    if (builder.tokenType === JavaSyntaxTokenType.PERMITS_KEYWORD) {
      refParser.parseReferenceList(builder, JavaSyntaxTokenType.PERMITS_KEYWORD, JavaSyntaxElementType.PERMITS_LIST, JavaSyntaxTokenType.COMMA)
    }

    if (builder.tokenType !== JavaSyntaxTokenType.LBRACE) {
      val error = builder.mark()
      while (BEFORE_LBRACE_ELEMENTS_SET.contains(builder.tokenType)) {
        builder.advanceLexer()
      }
      error.error(message("expected.lbrace"))
    }

    if (builder.tokenType === JavaSyntaxTokenType.LBRACE) {
      parseClassBodyWithBraces(builder, isAnnotation, isEnum)
    }

    JavaParserUtil.done(declaration, JavaSyntaxElementType.CLASS, languageLevel)
    return declaration
  }

  private fun parseEnumConstants(builder: SyntaxTreeBuilder) {
    var first = true
    while (builder.tokenType != null) {
      if (builder.expect(JavaSyntaxTokenType.SEMICOLON)) {
        return
      }

      if (builder.tokenType === JavaSyntaxTokenType.PRIVATE_KEYWORD || builder.tokenType === JavaSyntaxTokenType.PROTECTED_KEYWORD) {
        JavaParserUtil.error(builder, message("expected.semicolon"))
        return
      }

      val enumConstant = parseEnumConstant(builder)
      if (enumConstant == null && builder.tokenType === JavaSyntaxTokenType.COMMA && first) {
        val next = builder.lookAhead(1)
        if (next !== JavaSyntaxTokenType.SEMICOLON && next !== JavaSyntaxTokenType.RBRACE) {
          JavaParserUtil.error(builder, message("expected.identifier"))
        }
      }

      first = false

      var commaCount = 0
      while (builder.tokenType === JavaSyntaxTokenType.COMMA) {
        if (commaCount > 0) {
          JavaParserUtil.error(builder, message("expected.identifier"))
        }
        builder.advanceLexer()
        commaCount++
      }
      if (commaCount == 0 && builder.tokenType != null && builder.tokenType !== JavaSyntaxTokenType.SEMICOLON) {
        JavaParserUtil.error(builder, message("expected.comma.or.semicolon"))
        return
      }
    }
  }

  fun parseEnumConstant(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    val constant = builder.mark()

    parseModifierList(builder)

    if (builder.expect(JavaSyntaxTokenType.IDENTIFIER)) {
      if (builder.tokenType === JavaSyntaxTokenType.LPARENTH) {
        myParser.expressionParser.parseArgumentList(builder)
      }
      else {
        JavaParserUtil.emptyElement(builder, JavaSyntaxElementType.EXPRESSION_LIST)
      }

      if (builder.tokenType === JavaSyntaxTokenType.LBRACE) {
        val constantInit = builder.mark()
        parseClassBodyWithBraces(builder, false, false)
        JavaParserUtil.done(constantInit, JavaSyntaxElementType.ENUM_CONSTANT_INITIALIZER, languageLevel)
      }

      JavaParserUtil.done(constant, JavaSyntaxElementType.ENUM_CONSTANT, languageLevel)
      return constant
    }
    else {
      constant.rollbackTo()
      return null
    }
  }

  fun parseClassBodyDeclarations(builder: SyntaxTreeBuilder, isAnnotation: Boolean) {
    val context = if (isAnnotation) Context.ANNOTATION_INTERFACE else Context.CLASS

    var invalidElements: SyntaxTreeBuilder.Marker? = null
    while (true) {
      val tokenType = builder.tokenType
      if (tokenType == null || tokenType === JavaSyntaxTokenType.RBRACE) break

      if (tokenType === JavaSyntaxTokenType.SEMICOLON) {
        if (invalidElements != null) {
          invalidElements.error(message("unexpected.token"))
          invalidElements = null
        }
        builder.advanceLexer()
        continue
      }

      val declaration = parse(builder, context)
      if (declaration != null) {
        if (invalidElements != null) {
          invalidElements.errorBefore(message("unexpected.token"), declaration)
          invalidElements = null
        }
        continue
      }

      if (invalidElements == null) {
        invalidElements = builder.mark()
      }

      // adding a reference, not simple tokens allows "Browse ..." to work well
      val ref = myParser.referenceParser.parseJavaCodeReference(builder, true, true, false, false)
      if (ref == null) {
        builder.advanceLexer()
      }
    }

    invalidElements?.error(message("unexpected.token"))
  }

  open fun parse(builder: SyntaxTreeBuilder, context: Context?): SyntaxTreeBuilder.Marker? {
    val tokenType = builder.tokenType ?: return null

    if (tokenType === JavaSyntaxTokenType.LBRACE) {
      if (context == Context.FILE || context == Context.CODE_BLOCK) return null
    }
    else if (!isRecordToken(builder, tokenType) && !isSealedToken(builder, tokenType) && !isNonSealedToken(builder, tokenType, languageLevel)) {
      if (!TYPE_START.contains(tokenType) || tokenType === JavaSyntaxTokenType.AT) {
        if (!MODIFIER_BIT_SET.contains(tokenType) &&
            !CLASS_KEYWORD_BIT_SET.contains(tokenType) &&
            tokenType !== JavaSyntaxTokenType.AT &&
            (context === Context.CODE_BLOCK || tokenType !== JavaSyntaxTokenType.LT)
        ) {
          return null
        }
      }
    }

    val declaration = builder.mark()
    val declarationStart = builder.currentOffset

    val modListInfo = parseModifierList(builder)
    val modList = modListInfo.first

    if (builder.expect(JavaSyntaxTokenType.AT)) {
      if (builder.tokenType === JavaSyntaxTokenType.INTERFACE_KEYWORD) {
        return parseClassFromKeyword(builder, declaration, true, context) ?: modList
      }
      else {
        declaration.rollbackTo()
        return null
      }
    }

    if (CLASS_KEYWORD_BIT_SET.contains(builder.tokenType) || isRecordToken(builder, builder.tokenType)) {
      return parseClassFromKeyword(builder, declaration, false, context) ?: modList
    }

    val typeParams = if (builder.tokenType === JavaSyntaxTokenType.LT && context != Context.CODE_BLOCK) {
      myParser.referenceParser.parseTypeParameters(builder)
    }
    else null

    if (builder.tokenType === JavaSyntaxTokenType.LBRACE) {
      if (context == Context.CODE_BLOCK) {
        JavaParserUtil.error(builder, message("expected.identifier.or.type"), null)
        declaration.drop()
        return modList
      }

      val codeBlock = checkNotNull(myParser.statementParser.parseCodeBlock(builder)) { builder.text }
      if (typeParams != null) {
        val error = typeParams.precede()
        error.errorBefore(message("unexpected.token"), codeBlock)
      }

      JavaParserUtil.done(declaration, JavaSyntaxElementType.CLASS_INITIALIZER, languageLevel)
      return declaration
    }

    var type: ReferenceParser.TypeInfo? = null

    if (TYPE_START.contains(builder.tokenType)) {
      val pos = builder.mark()

      var flags = ReferenceParser.EAT_LAST_DOT or ReferenceParser.WILDCARD
      if (context == Context.CODE_BLOCK || context == Context.JSHELL) {
        flags = flags or ReferenceParser.VAR_TYPE
      }
      type = myParser.referenceParser.parseTypeInfo(builder, flags)

      if (type == null) {
        pos.rollbackTo()
      }
      else if (builder.tokenType === JavaSyntaxTokenType.LPARENTH ||
               builder.tokenType === JavaSyntaxTokenType.LBRACE ||
               builder.tokenType === JavaSyntaxTokenType.THROWS_KEYWORD
      ) {  // constructor
        if (context == Context.CODE_BLOCK) {
          declaration.rollbackTo()
          return null
        }

        pos.rollbackTo()
        if (typeParams == null) {
          JavaParserUtil.emptyElement(builder, JavaSyntaxElementType.TYPE_PARAMETER_LIST)
        }
        parseAnnotations(builder)

        if (!builder.expect(JavaSyntaxTokenType.IDENTIFIER)) {
          val primitive = builder.mark()
          builder.advanceLexer()
          primitive.error(message("expected.identifier"))
        }

        when (builder.tokenType) {
          JavaSyntaxTokenType.LPARENTH -> {
            return parseMethodFromLeftParenth(builder, declaration, false, true)
          }
          JavaSyntaxTokenType.LBRACE -> { // compact constructor
            JavaParserUtil.emptyElement(builder, JavaSyntaxElementType.THROWS_LIST)
            return parseMethodBody(builder, declaration, false)
          }
          JavaSyntaxTokenType.THROWS_KEYWORD -> {
            myParser.referenceParser.parseReferenceList(
              /* builder = */ builder,
              /* start = */ JavaSyntaxTokenType.THROWS_KEYWORD,
              /* type = */ JavaSyntaxElementType.THROWS_LIST,
              /* delimiter = */ JavaSyntaxTokenType.COMMA
            )
            return parseMethodBody(builder, declaration, false)
          }
          else -> {
            declaration.rollbackTo()
            return null
          }
        }
      }
      else {
        pos.drop()
      }
    }

    if (type == null) {
      val error = typeParams?.precede() ?: builder.mark()
      error.error(message("expected.identifier.or.type"))
      declaration.drop()
      return modList
    }

    if (!builder.expect(JavaSyntaxTokenType.IDENTIFIER)) {
      if (context != Context.CODE_BLOCK ||
          !modListInfo.second ||
          (type.isPrimitive && builder.tokenType !== JavaSyntaxTokenType.DOT)
      ) {
        typeParams?.precede()?.errorBefore(message("unexpected.token"), type.marker)
        builder.error(message("expected.identifier"))
        declaration.drop()
        return modList
      }
      else {
        declaration.rollbackTo()
        return null
      }
    }

    if (builder.tokenType === JavaSyntaxTokenType.LPARENTH) {
      if (context == Context.CLASS || context == Context.ANNOTATION_INTERFACE || context == Context.FILE || context == Context.JSHELL) {  // method
        if (typeParams == null) {
          JavaParserUtil.emptyElement(type.marker, JavaSyntaxElementType.TYPE_PARAMETER_LIST)
        }
        return parseMethodFromLeftParenth(builder, declaration, (context == Context.ANNOTATION_INTERFACE), false)
      }
    }

    typeParams?.precede()?.errorBefore(message("unexpected.token"), type.marker)
    return parseFieldOrLocalVariable(builder, declaration, declarationStart, context)
  }

  fun isRecordToken(builder: SyntaxTreeBuilder, tokenType: SyntaxElementType?): Boolean {
    if (tokenType === JavaSyntaxTokenType.IDENTIFIER && JavaKeywords.RECORD == builder.tokenText) {
      val nextToken = builder.lookAhead(1)
      if (nextToken === JavaSyntaxTokenType.IDENTIFIER ||  // The following tokens cannot be part of a valid record declaration,
          // but we assume it to be a malformed record, rather than a malformed type.
          MODIFIER_BIT_SET.contains(nextToken) ||
          CLASS_KEYWORD_BIT_SET.contains(nextToken) ||
          TYPE_START.contains(nextToken) ||
          nextToken === JavaSyntaxTokenType.AT ||
          nextToken === JavaSyntaxTokenType.LBRACE ||
          nextToken === JavaSyntaxTokenType.RBRACE
      ) {
        val level = languageLevel
        return JavaFeature.RECORDS.isSufficient(level)
      }
    }
    return false
  }

  private fun isSealedToken(builder: SyntaxTreeBuilder, tokenType: SyntaxElementType?): Boolean {
    return JavaFeature.SEALED_CLASSES.isSufficient(languageLevel) &&
           tokenType === JavaSyntaxTokenType.IDENTIFIER &&
           JavaKeywords.SEALED == builder.tokenText
  }

  private fun isValueToken(builder: SyntaxTreeBuilder, tokenType: SyntaxElementType?): Boolean {
    return JavaFeature.VALHALLA_VALUE_CLASSES.isSufficient(languageLevel) &&
           tokenType === JavaSyntaxTokenType.IDENTIFIER &&
           JavaKeywords.VALUE == builder.tokenText
  }

  @JvmOverloads
  fun parseModifierList(
    builder: SyntaxTreeBuilder,
    modifiers: SyntaxElementTypeSet = MODIFIER_BIT_SET
  ): Pair<SyntaxTreeBuilder.Marker, Boolean> {
    val modList = builder.mark()
    var isEmpty = true

    while (true) {
      var tokenType = builder.tokenType ?: break
      if (isValueToken(builder, tokenType)) {
        builder.remapCurrentToken(JavaSyntaxTokenType.VALUE_KEYWORD)
        tokenType = JavaSyntaxTokenType.VALUE_KEYWORD
      }
      else if (isSealedToken(builder, tokenType)) {
        builder.remapCurrentToken(JavaSyntaxTokenType.SEALED_KEYWORD)
        tokenType = JavaSyntaxTokenType.SEALED_KEYWORD
      }
      if (isNonSealedToken(builder, tokenType, languageLevel)) {
        val nonSealed = builder.mark()
        builder.advance(3)
        nonSealed.collapse(JavaSyntaxTokenType.NON_SEALED_KEYWORD)
        isEmpty = false
      }
      else if (modifiers.contains(tokenType)) {
        builder.advanceLexer()
        isEmpty = false
      }
      else if (tokenType === JavaSyntaxTokenType.AT) {
        if (KEYWORD_BIT_SET.contains(builder.lookAhead(1))) {
          break
        }
        parseAnnotation(builder)
        isEmpty = false
      }
      else {
        break
      }
    }

    JavaParserUtil.done(modList, JavaSyntaxElementType.MODIFIER_LIST, languageLevel)
    return modList to isEmpty
  }

  private fun parseMethodFromLeftParenth(
    builder: SyntaxTreeBuilder,
    declaration: SyntaxTreeBuilder.Marker,
    anno: Boolean,
    constructor: Boolean
  ): SyntaxTreeBuilder.Marker {
    parseParameterList(builder)

    eatBrackets(builder, if (constructor) "expected.lbrace" else null)

    myParser.referenceParser
      .parseReferenceList(builder, JavaSyntaxTokenType.THROWS_KEYWORD, JavaSyntaxElementType.THROWS_LIST, JavaSyntaxTokenType.COMMA)

    if (anno && builder.expect(JavaSyntaxTokenType.DEFAULT_KEYWORD) && parseAnnotationValue(builder) == null) {
      JavaParserUtil.error(builder, message("expected.value"))
    }

    return parseMethodBody(builder, declaration, anno)
  }

  private fun parseMethodBody(
    builder: SyntaxTreeBuilder,
    declaration: SyntaxTreeBuilder.Marker,
    anno: Boolean
  ): SyntaxTreeBuilder.Marker {
    val tokenType = builder.tokenType
    if (tokenType !== JavaSyntaxTokenType.SEMICOLON && tokenType !== JavaSyntaxTokenType.LBRACE) {
      val error = builder.mark()
      // heuristic: going to next line obviously means method signature is over, starting new method (actually, another one completion hack)
      val text = builder.text
      Loop@ while (true) {
        for (i in builder.currentOffset - 1 downTo 0) {
          val ch = text[i]
          if (ch == '\n') {
            break@Loop
          }
          else if (ch != ' ' && ch != '\t') {
            break
          }
        }
        if (!builder.expect(APPEND_TO_METHOD_SET)) break
      }
      error.error(message("expected.lbrace.or.semicolon"))
    }

    if (!builder.expect(JavaSyntaxTokenType.SEMICOLON)) {
      if (builder.tokenType === JavaSyntaxTokenType.LBRACE) {
        myParser.statementParser.parseCodeBlock(builder)
      }
    }

    JavaParserUtil.done(marker = declaration,
                        type = if (anno) JavaSyntaxElementType.ANNOTATION_METHOD else JavaSyntaxElementType.METHOD,
                        languageLevel = languageLevel
    )
    return declaration
  }

  fun parseParameterList(builder: SyntaxTreeBuilder) {
    parseElementList(builder, ListType.METHOD)
  }

  fun parseResourceList(builder: SyntaxTreeBuilder) {
    parseElementList(builder, ListType.RESOURCE)
  }

  fun parseLambdaParameterList(builder: SyntaxTreeBuilder, typed: Boolean) {
    parseElementList(builder, if (typed) ListType.LAMBDA_TYPED else ListType.LAMBDA_UNTYPED)
  }

  private fun parseElementList(builder: SyntaxTreeBuilder, type: ListType) {
    val lambda = (type == ListType.LAMBDA_TYPED || type == ListType.LAMBDA_UNTYPED)
    val resources = (type == ListType.RESOURCE)
    val elementList = builder.mark()
    val leftParenth = builder.expect(JavaSyntaxTokenType.LPARENTH)
    require(lambda || leftParenth) { builder.tokenType ?: "<null>" }

    val delimiter = if (resources) JavaSyntaxTokenType.SEMICOLON else JavaSyntaxTokenType.COMMA
    val noDelimiterMsg = if (resources) "expected.semicolon" else "expected.comma"
    val noElementMsg = if (resources) "expected.resource" else "expected.parameter"

    data class ErrorState(
      val marker: SyntaxTreeBuilder.Marker,
      val errorMessage: @Nls String
    )

    var errorState: ErrorState? = null
    var delimiterExpected = false
    var noElements = true
    while (true) {
      val tokenType = builder.tokenType
      if (tokenType == null || type.stopperTypes.contains(tokenType)) {
        val noLastElement = !delimiterExpected && (!noElements && !resources || noElements && resources)
        if (noLastElement) {
          val key = if (lambda) "expected.parameter" else "expected.identifier.or.type"
          JavaParserUtil.error(builder, message(key))
        }
        if (tokenType === JavaSyntaxTokenType.RPARENTH) {
          errorState?.let { (invalidElements, errorMessage) ->
            invalidElements.error(errorMessage)
            errorState = null
          }
          builder.advanceLexer()
        }
        else {
          if (!noLastElement || resources) {
            errorState?.let { (invalidElements, errorMessage) ->
              invalidElements.error(errorMessage)
              errorState = null
            }
            if (leftParenth) {
              JavaParserUtil.error(builder, message("expected.rparen"))
            }
          }
        }
        break
      }

      if (delimiterExpected) {
        if (builder.tokenType === delimiter) {
          delimiterExpected = false
          errorState?.let { (invalidElements, errorMessage) ->
            invalidElements.error(errorMessage)
            errorState = null
          }
          builder.advanceLexer()
          continue
        }
      }
      else {
        val listElement =
          if (type == ListType.RECORD_COMPONENTS) parseParameterOrRecordComponent(builder, true, false, false, false)
          else if (resources) parseResource(builder)
          else if (lambda) parseLambdaParameter(builder, type == ListType.LAMBDA_TYPED)
          else parseParameter(builder, true, false, false)
        if (listElement != null) {
          delimiterExpected = true
          errorState?.let { (invalidElements, errorMessage) ->
            invalidElements.errorBefore(errorMessage, listElement)
            errorState = null
          }
          noElements = false
          continue
        }
      }

      if (errorState == null) {
        if (builder.tokenType === delimiter) {
          JavaParserUtil.error(builder, message(noElementMsg))
          builder.advanceLexer()
          if (noElements && resources) {
            noElements = false
          }
          continue
        }
        else {
          errorState = ErrorState(
            marker = builder.mark(),
            errorMessage = message(if (delimiterExpected) noDelimiterMsg else noElementMsg)
          )
        }
      }

      // adding a reference, not simple tokens allows "Browse .." to work well
      val ref = myParser.referenceParser.parseJavaCodeReference(builder, true, true, false, false)
      if (ref == null && builder.tokenType != null) {
        builder.advanceLexer()
      }
    }

    errorState?.let { (marker, error) ->
      marker.error(error)
    }

    JavaParserUtil.done(elementList, type.nodeType, languageLevel)
  }

  fun parseParameter(
    builder: SyntaxTreeBuilder,
    ellipsis: Boolean,
    disjunctiveType: Boolean,
    varType: Boolean
  ): SyntaxTreeBuilder.Marker? {
    return parseParameterOrRecordComponent(builder, ellipsis, disjunctiveType, varType, true)
  }

  fun parseParameterOrRecordComponent(
    builder: SyntaxTreeBuilder,
    ellipsis: Boolean,
    disjunctiveType: Boolean,
    varType: Boolean,
    isParameter: Boolean
  ): SyntaxTreeBuilder.Marker? {
    var typeFlags = 0
    if (ellipsis) typeFlags = typeFlags or ReferenceParser.ELLIPSIS
    if (disjunctiveType) typeFlags = typeFlags or ReferenceParser.DISJUNCTIONS
    if (varType) typeFlags = typeFlags or ReferenceParser.VAR_TYPE
    return parseListElement(builder = builder,
                            typed = true,
                            typeFlags = typeFlags,
                            type = if (isParameter) JavaSyntaxElementType.PARAMETER else JavaSyntaxElementType.RECORD_COMPONENT)
  }

  fun parseResource(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    val marker = builder.mark()

    val expr = myParser.expressionParser.parse(builder)
    if (expr != null &&
        RESOURCE_EXPRESSIONS.contains(JavaParserUtil.exprType(expr)) &&
        builder.tokenType !== JavaSyntaxTokenType.IDENTIFIER
      ) {
      marker.done(JavaSyntaxElementType.RESOURCE_EXPRESSION)
      return marker
    }

    marker.rollbackTo()

    return parseListElement(builder, true, ReferenceParser.VAR_TYPE, JavaSyntaxElementType.RESOURCE_VARIABLE)
  }

  fun parseLambdaParameter(builder: SyntaxTreeBuilder, typed: Boolean): SyntaxTreeBuilder.Marker? {
    var flags = ReferenceParser.ELLIPSIS
    if (JavaFeature.VAR_LAMBDA_PARAMETER.isSufficient(languageLevel)) {
      flags = flags or ReferenceParser.VAR_TYPE
    }
    return parseListElement(builder = builder,
                            typed = typed,
                            typeFlags = flags,
                            type = JavaSyntaxElementType.PARAMETER)
  }


  private fun parseListElement(
    builder: SyntaxTreeBuilder,
    typed: Boolean,
    typeFlags: Int,
    type: SyntaxElementType?
  ): SyntaxTreeBuilder.Marker? {
    val param = builder.mark()

    val modListInfo = parseModifierList(builder)

    val typeInfo: ReferenceParser.TypeInfo?
    if (typed) {
      val flags = ReferenceParser.EAT_LAST_DOT or ReferenceParser.WILDCARD or typeFlags
      typeInfo = myParser.referenceParser.parseTypeInfo(builder, flags)

      if (typeInfo == null) {
        if (modListInfo.second) {
          param.rollbackTo()
          return null
        }
        else {
          JavaParserUtil.error(builder, message("expected.type"))
          JavaParserUtil.emptyElement(builder, JavaSyntaxElementType.TYPE)
        }
      }
    }

    if (typed) {
      val tokenType = builder.tokenType
      if (tokenType === JavaSyntaxTokenType.THIS_KEYWORD ||
          tokenType === JavaSyntaxTokenType.IDENTIFIER && builder.lookAhead(1) === JavaSyntaxTokenType.DOT
      ) {
        val mark = builder.mark()

        val expr = myParser.expressionParser.parse(builder)
        if (expr != null && JavaParserUtil.exprType(expr) === JavaSyntaxElementType.THIS_EXPRESSION) {
          mark.drop()
          JavaParserUtil.done(param, JavaSyntaxElementType.RECEIVER_PARAMETER, languageLevel)
          return param
        }

        mark.rollbackTo()
      }
    }

    if (builder.expect(JavaSyntaxTokenType.IDENTIFIER)) {
      if (type === JavaSyntaxElementType.PARAMETER || type === JavaSyntaxElementType.RECORD_COMPONENT) {
        eatBrackets(builder, null)
        JavaParserUtil.done(param, type, languageLevel)
        return param
      }
    }
    else {
      JavaParserUtil.error(builder, message("expected.identifier"))
      param.drop()
      return modListInfo.first
    }

    if (JavaParserUtil.expectOrError(builder, JavaSyntaxTokenType.EQ, "expected.eq")) {
      if (myParser.expressionParser.parse(builder) == null) {
        JavaParserUtil.error(builder, message("expected.expression"))
      }
    }

    JavaParserUtil.done(param, JavaSyntaxElementType.RESOURCE_VARIABLE, languageLevel)
    return param
  }

  private fun parseFieldOrLocalVariable(
    builder: SyntaxTreeBuilder,
    declaration: SyntaxTreeBuilder.Marker,
    declarationStart: Int,
    context: Context?
  ): SyntaxTreeBuilder.Marker? {
    val varType = when (context) {
      Context.CLASS, Context.ANNOTATION_INTERFACE, Context.FILE, Context.JSHELL -> JavaSyntaxElementType.FIELD
      Context.CODE_BLOCK -> JavaSyntaxElementType.LOCAL_VARIABLE
      else -> {
        declaration.drop()
        require(false) { "Unexpected context: $context" }
        return null
      }
    }

    var variable = declaration
    var unclosed = false
    var eatSemicolon = true
    var shouldRollback: Boolean
    var openMarker = true
    while (true) {
      shouldRollback = true

      if (!eatBrackets(builder, null)) {
        unclosed = true
      }

      if (builder.expect(JavaSyntaxTokenType.EQ)) {
        val expr = myParser.expressionParser.parse(builder)
        if (expr != null) {
          shouldRollback = false
        }
        else {
          JavaParserUtil.error(builder, message("expected.expression"))
          unclosed = true
          break
        }
      }

      if (builder.tokenType !== JavaSyntaxTokenType.COMMA) {
        break
      }
      JavaParserUtil.done(variable, varType, languageLevel)
      builder.advanceLexer()

      if (builder.tokenType !== JavaSyntaxTokenType.IDENTIFIER) {
        JavaParserUtil.error(builder, message("expected.identifier"))
        unclosed = true
        eatSemicolon = false
        openMarker = false
        break
      }

      variable = builder.mark()
      builder.advanceLexer()
    }

    if (builder.tokenType === JavaSyntaxTokenType.SEMICOLON && eatSemicolon) {
      builder.advanceLexer()
    }
    else {
      // special treatment (see DeclarationParserTest.testMultiLineUnclosed())
      if (!builder.eof() && shouldRollback) {
        val text = builder.text
        val spaceEnd = builder.currentOffset
        val spaceStart = text.shiftBackward(spaceEnd - 1, WHITESPACES)
        val lineStart = text.shiftBackwardUntil(spaceEnd, LINE_ENDS)

        if (declarationStart < lineStart && lineStart < spaceStart) {
          val newBufferEnd = text.shiftForward(WHITESPACES, lineStart)
          declaration.rollbackTo()
          return parse(JavaParserUtil.stoppingBuilder(builder, newBufferEnd), context)
        }
      }

      if (!unclosed) {
        JavaParserUtil.error(builder, message("expected.semicolon"))
      }
    }

    if (openMarker) {
      JavaParserUtil.done(variable, varType, languageLevel)
    }

    return declaration
  }

  private fun eatBrackets(
    builder: SyntaxTreeBuilder,
    errorKey: @PropertyKey(resourceBundle = JavaSyntaxBundle.BUNDLE) String?
  ): Boolean {
    val tokenType = builder.tokenType
    if (tokenType !== JavaSyntaxTokenType.LBRACKET && tokenType !== JavaSyntaxTokenType.AT) return true

    val marker = builder.mark()

    var count = 0
    while (true) {
      parseAnnotations(builder)
      if (!builder.expect(JavaSyntaxTokenType.LBRACKET)) {
        break
      }
      ++count
      if (!builder.expect(JavaSyntaxTokenType.RBRACKET)) {
        break
      }
      ++count
    }

    if (count == 0) {
      // just annotation, most probably belongs to a next declaration
      marker.rollbackTo()
      return true
    }

    if (errorKey != null) {
      marker.error(message(errorKey))
    }
    else {
      marker.drop()
    }

    val paired = count % 2 == 0
    if (!paired) {
      JavaParserUtil.error(builder, message("expected.rbracket"))
    }
    return paired
  }

  fun parseAnnotations(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    var firstAnno: SyntaxTreeBuilder.Marker? = null

    while (builder.tokenType === JavaSyntaxTokenType.AT) {
      val anno = parseAnnotation(builder)
      if (firstAnno == null) firstAnno = anno
    }

    return firstAnno
  }

  fun parseAnnotation(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    require(builder.tokenType === JavaSyntaxTokenType.AT) { builder.tokenType ?: "<null>" }
    val anno = builder.mark()
    builder.advanceLexer()

    val classRef = if (builder.tokenType === JavaSyntaxTokenType.IDENTIFIER) {
      myParser.referenceParser.parseJavaCodeReference(builder, true, false, false, false)
    }
    else null

    if (classRef == null) {
      JavaParserUtil.error(builder, message("expected.class.reference"))
    }

    parseAnnotationParameterList(builder)

    JavaParserUtil.done(anno, JavaSyntaxElementType.ANNOTATION, languageLevel)
    return anno
  }

  private fun parseAnnotationParameterList(builder: SyntaxTreeBuilder) {
    val list = builder.mark()

    if (!builder.expect(JavaSyntaxTokenType.LPARENTH) ||
        builder.expect(JavaSyntaxTokenType.RPARENTH)
    ) {
      JavaParserUtil.done(list, JavaSyntaxElementType.ANNOTATION_PARAMETER_LIST, languageLevel)
      return
    }

    if (builder.tokenType == null) {
      JavaParserUtil.error(builder, message("expected.parameter.or.rparen"))
      JavaParserUtil.done(list, JavaSyntaxElementType.ANNOTATION_PARAMETER_LIST, languageLevel)
      return
    }
    var elementMarker = parseAnnotationElement(builder)
    while (true) {
      var tokenType = builder.tokenType
      when {
        tokenType == null -> {
          JavaParserUtil.error(builder, message(if (elementMarker == null) "expected.parameter.or.rparen" else "expected.comma.or.rparen"))
          break
        }

        builder.expect(JavaSyntaxTokenType.RPARENTH) -> {
          break
        }

        tokenType === JavaSyntaxTokenType.COMMA -> {
          builder.advanceLexer()
          elementMarker = parseAnnotationElement(builder)
          if (elementMarker == null) {
            JavaParserUtil.error(builder, message("annotation.name.is.missing"))
            tokenType = builder.tokenType
            if (tokenType !== JavaSyntaxTokenType.COMMA && tokenType !== JavaSyntaxTokenType.RPARENTH) {
              break
            }
          }
        }

        else -> {
          JavaParserUtil.error(builder, message(if (elementMarker == null) "expected.parameter.or.rparen" else "expected.comma.or.rparen"))
          tokenType = builder.lookAhead(1)
          if (tokenType !== JavaSyntaxTokenType.COMMA && tokenType !== JavaSyntaxTokenType.RPARENTH) break
          builder.advanceLexer()
        }
      }
    }

    JavaParserUtil.done(list, JavaSyntaxElementType.ANNOTATION_PARAMETER_LIST, languageLevel)
  }

  private fun parseAnnotationElement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    var pair = builder.mark()

    var valueMarker = parseAnnotationValue(builder)
    if (valueMarker == null && builder.tokenType !== JavaSyntaxTokenType.EQ) {
      pair.drop()
      return null
    }
    if (builder.tokenType !== JavaSyntaxTokenType.EQ) {
      JavaParserUtil.done(pair, JavaSyntaxElementType.NAME_VALUE_PAIR, languageLevel)
      return pair
    }

    pair.rollbackTo()
    pair = builder.mark()

    JavaParserUtil.expectOrError(builder, JavaSyntaxTokenType.IDENTIFIER, "expected.identifier")
    builder.expect(JavaSyntaxTokenType.EQ)
    valueMarker = parseAnnotationValue(builder)
    if (valueMarker == null) JavaParserUtil.error(builder, message("expected.value"))

    JavaParserUtil.done(pair, JavaSyntaxElementType.NAME_VALUE_PAIR, languageLevel)
    return pair
  }

  fun parseAnnotationValue(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    val tokenType = builder.tokenType
    return when (tokenType) {
      JavaSyntaxTokenType.AT -> parseAnnotation(builder)
      JavaSyntaxTokenType.LBRACE -> {
        myParser.expressionParser.parseArrayInitializer(
          builder = builder,
          type = JavaSyntaxElementType.ANNOTATION_ARRAY_INITIALIZER,
          elementParser = { builder -> parseAnnotationValue(builder) },
          missingElementKey = "expected.value")
      }
      else -> myParser.expressionParser.parseConditional(builder)
    }
  }
}

fun isNonSealedToken(builder: SyntaxTreeBuilder, tokenType: SyntaxElementType?, level: LanguageLevel): Boolean {
  if (!JavaFeature.SEALED_CLASSES.isSufficient(level) ||
      tokenType !== JavaSyntaxTokenType.IDENTIFIER ||
      "non" != builder.tokenText ||
      builder.lookAhead(1) !== JavaSyntaxTokenType.MINUS ||
      builder.lookAhead(2) !== JavaSyntaxTokenType.IDENTIFIER
  ) {
    return false
  }
  val maybeNonSealed = builder.mark()
  builder.advance(2)
  val isNonSealed = JavaKeywords.SEALED == builder.tokenText
  maybeNonSealed.rollbackTo()
  return isNonSealed
}

private enum class ListType {
  METHOD,
  RESOURCE,
  LAMBDA_TYPED,
  LAMBDA_UNTYPED,
  RECORD_COMPONENTS;

  val nodeType: SyntaxElementType
    get() = when (this) {
      RESOURCE -> JavaSyntaxElementType.RESOURCE_LIST
      RECORD_COMPONENTS -> JavaSyntaxElementType.RECORD_HEADER
      else -> JavaSyntaxElementType.PARAMETER_LIST
    }

  val stopperTypes: SyntaxElementTypeSet
    get() = when (this) {
      METHOD -> METHOD_PARAM_LIST_STOPPERS
      else -> PARAM_LIST_STOPPERS
    }
}

private val TYPE_START: SyntaxElementTypeSet = PRIMITIVE_TYPE_BIT_SET + setOf(JavaSyntaxTokenType.IDENTIFIER, JavaSyntaxTokenType.AT, JavaSyntaxTokenType.VAR_KEYWORD)

private val RESOURCE_EXPRESSIONS: SyntaxElementTypeSet = syntaxElementTypeSetOf(
  JavaSyntaxElementType.REFERENCE_EXPRESSION, JavaSyntaxElementType.THIS_EXPRESSION, JavaSyntaxElementType.METHOD_CALL_EXPRESSION,
  JavaSyntaxElementType.NEW_EXPRESSION
)

private val BEFORE_LBRACE_ELEMENTS_SET: SyntaxElementTypeSet = syntaxElementTypeSetOf(
  JavaSyntaxTokenType.IDENTIFIER, JavaSyntaxTokenType.COMMA, JavaSyntaxTokenType.EXTENDS_KEYWORD,
  JavaSyntaxTokenType.IMPLEMENTS_KEYWORD, JavaSyntaxTokenType.LPARENTH
)

private val APPEND_TO_METHOD_SET: SyntaxElementTypeSet = syntaxElementTypeSetOf(
  JavaSyntaxTokenType.IDENTIFIER, JavaSyntaxTokenType.COMMA, JavaSyntaxTokenType.THROWS_KEYWORD
)

private val PARAM_LIST_STOPPERS: SyntaxElementTypeSet = syntaxElementTypeSetOf(
  JavaSyntaxTokenType.RPARENTH, JavaSyntaxTokenType.LBRACE, JavaSyntaxTokenType.ARROW
)

private val METHOD_PARAM_LIST_STOPPERS: SyntaxElementTypeSet = syntaxElementTypeSetOf(
  JavaSyntaxTokenType.RPARENTH, JavaSyntaxTokenType.LBRACE, JavaSyntaxTokenType.ARROW, JavaSyntaxTokenType.SEMICOLON
)

private const val WHITESPACES = "\n\r \t"
private const val LINE_ENDS = "\n\r"