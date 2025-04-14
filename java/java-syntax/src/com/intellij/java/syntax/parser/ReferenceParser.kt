// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.parser

import com.intellij.java.syntax.JavaSyntaxBundle.message
import com.intellij.java.syntax.element.JavaSyntaxElementType
import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.java.syntax.element.SyntaxElementTypes
import com.intellij.java.syntax.parser.JavaParserUtil.emptyElement
import com.intellij.java.syntax.parser.JavaParserUtil.error
import com.intellij.java.syntax.parser.JavaParserUtil.expectOrError
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.expect
import com.intellij.pom.java.JavaFeature
import com.intellij.util.BitUtil.isSet
import com.intellij.util.BitUtil.set

class ReferenceParser(private val myParser: JavaParser) {
  class TypeInfo {
    var isPrimitive: Boolean = false
    var isParameterized: Boolean = false
    var isArray: Boolean = false
    var isVarArg: Boolean = false
    var hasErrors: Boolean = false
    lateinit var marker: SyntaxTreeBuilder.Marker
  }

  fun parseType(builder: SyntaxTreeBuilder, flags: Int): SyntaxTreeBuilder.Marker? {
    val typeInfo = parseTypeInfo(builder, flags)
    return typeInfo?.marker
  }

  fun parseTypeInfo(builder: SyntaxTreeBuilder, flags: Int): TypeInfo? {
    val typeInfo = parseTypeInfo(builder, flags, false)

    if (typeInfo != null) {
      assert(!isSet(flags, DISJUNCTIONS) || !isSet(flags, CONJUNCTIONS)) { "don't set both flags simultaneously" }
      val operator = if (isSet(flags, DISJUNCTIONS)) JavaSyntaxTokenType.OR
      else if (isSet(flags, CONJUNCTIONS)) JavaSyntaxTokenType.AND else null

      if (operator != null && builder.tokenType === operator) {
        typeInfo.marker = typeInfo.marker.precede()

        while (builder.tokenType === operator) {
          builder.advanceLexer()
          val tokenType = builder.tokenType
          if (tokenType !== JavaSyntaxTokenType.IDENTIFIER && tokenType !== JavaSyntaxTokenType.AT) {
            error(builder, message("expected.identifier"))
          }
          parseTypeInfo(builder, flags, false)
        }

        typeInfo.marker.done(JavaSyntaxElementType.TYPE)
      }
    }

    return typeInfo
  }

  private fun parseTypeInfo(builder: SyntaxTreeBuilder, flags: Int, badWildcard: Boolean): TypeInfo? {
    if (builder.tokenType === null) return null

    val typeInfo = TypeInfo()

    var type = builder.mark()
    val anno = myParser.declarationParser.parseAnnotations(builder)

    var tokenType = builder.tokenType
    if (tokenType === JavaSyntaxTokenType.IDENTIFIER &&
        isSet(flags, VAR_TYPE) &&
        builder.lookAhead(1) !== JavaSyntaxTokenType.DOT &&
        builder.lookAhead(1) !== JavaSyntaxTokenType.COLON &&
        JavaKeywords.VAR == builder.tokenText &&
        JavaFeature.LVTI.isSufficient(myParser.languageLevel)
    ) {
      builder.remapCurrentToken(JavaSyntaxTokenType.VAR_KEYWORD.also { tokenType = it })
    }
    else if (tokenType === JavaSyntaxTokenType.VAR_KEYWORD && !isSet(flags, VAR_TYPE)) {
      builder.remapCurrentToken(JavaSyntaxTokenType.IDENTIFIER.also { tokenType = it })
    }

    if (builder.expect(SyntaxElementTypes.PRIMITIVE_TYPE_BIT_SET)) {
      typeInfo.isPrimitive = true
    }
    else if ((isSet(flags, WILDCARD) || badWildcard) && (tokenType === JavaSyntaxTokenType.QUEST)) {
      builder.advanceLexer()
      completeWildcardType(builder, isSet(flags, WILDCARD), type)
      typeInfo.marker = type
      return typeInfo
    }
    else if (tokenType === JavaSyntaxTokenType.IDENTIFIER) {
      parseJavaCodeReference(builder, isSet(flags, EAT_LAST_DOT), true, false, false, false, isSet(flags, DIAMONDS), typeInfo)
    }
    else if (tokenType === JavaSyntaxTokenType.VAR_KEYWORD) {
      builder.advanceLexer()
      type.done(JavaSyntaxElementType.TYPE)
      typeInfo.marker = type
      return typeInfo
    }
    else if (isSet(flags, DIAMONDS) && tokenType === JavaSyntaxTokenType.GT) {
      if (anno == null) {
        emptyElement(builder, JavaSyntaxElementType.DIAMOND_TYPE)
      }
      else {
        error(builder, message("expected.identifier"))
        typeInfo.hasErrors = true
      }
      type.done(JavaSyntaxElementType.TYPE)
      typeInfo.marker = type
      return typeInfo
    }
    else {
      type.drop()
      if (anno != null && isSet(flags, INCOMPLETE_ANNO)) {
        error(builder, message("expected.type"))
        typeInfo.marker = anno
        typeInfo.hasErrors = true
        return typeInfo
      }
      return null
    }

    type.done(JavaSyntaxElementType.TYPE)
    while (true) {
      myParser.declarationParser.parseAnnotations(builder)

      val bracket = builder.mark()
      if (!builder.expect(JavaSyntaxTokenType.LBRACKET)) {
        bracket.drop()
        break
      }
      if (!builder.expect(JavaSyntaxTokenType.RBRACKET)) {
        bracket.rollbackTo()
        break
      }
      bracket.drop()
      typeInfo.isArray = true
    }

    if (isSet(flags, ELLIPSIS) && builder.tokenType === JavaSyntaxTokenType.ELLIPSIS) {
      builder.advanceLexer()
      typeInfo.isVarArg = true
    }

    if (typeInfo.isVarArg || typeInfo.isArray) {
      type = type.precede()
      type.done(JavaSyntaxElementType.TYPE)
    }

    typeInfo.marker = type
    return typeInfo
  }

  private fun completeWildcardType(builder: SyntaxTreeBuilder, wildcard: Boolean, type: SyntaxTreeBuilder.Marker) {
    if (builder.expect(WILDCARD_KEYWORD_SET)) {
      if (parseTypeInfo(builder, EAT_LAST_DOT) == null) {
        error(builder, message("expected.type"))
      }
    }

    if (wildcard) {
      type.done(JavaSyntaxElementType.TYPE)
    }
    else {
      type.error(message("error.message.wildcard.not.expected"))
    }
  }

  fun parseJavaCodeReference(
    builder: SyntaxTreeBuilder,
    eatLastDot: Boolean,
    parameterList: Boolean,
    isNew: Boolean,
    diamonds: Boolean
  ): SyntaxTreeBuilder.Marker? {
    return parseJavaCodeReference(builder, eatLastDot, parameterList, false, false, isNew, diamonds, TypeInfo())
  }

  fun parseImportCodeReference(builder: SyntaxTreeBuilder, isStatic: Boolean): Boolean {
    val typeInfo = TypeInfo()
    parseJavaCodeReference(builder, true, false, true, isStatic, false, false, typeInfo)
    return !typeInfo.hasErrors
  }

  private fun parseJavaCodeReference(
    builder: SyntaxTreeBuilder, eatLastDot: Boolean, parameterList: Boolean, isImport: Boolean,
    isStaticImport: Boolean, isNew: Boolean, diamonds: Boolean,
    typeInfo: TypeInfo
  ): SyntaxTreeBuilder.Marker? {
    var refElement = builder.mark()

    myParser.declarationParser.parseAnnotations(builder)

    if (!builder.expect(JavaSyntaxTokenType.IDENTIFIER)) {
      refElement.rollbackTo()
      if (isImport) {
        error(builder, message("expected.identifier"))
      }
      typeInfo.hasErrors = true
      return null
    }

    if (parameterList) {
      typeInfo.isParameterized = parseReferenceParameterList(builder, true, diamonds)
    }
    else if (!isStaticImport) {
      emptyElement(builder, JavaSyntaxElementType.REFERENCE_PARAMETER_LIST)
    }

    while (builder.tokenType === JavaSyntaxTokenType.DOT) {
      refElement.done(JavaSyntaxElementType.JAVA_CODE_REFERENCE)

      if (isNew && !diamonds && typeInfo.isParameterized) {
        return refElement
      }

      val dotPos = builder.mark()
      builder.advanceLexer()

      myParser.declarationParser.parseAnnotations(builder)

      if (isImport && builder.expect(JavaSyntaxTokenType.ASTERISK)) {
        dotPos.drop()
        return refElement
      }
      else if (!builder.expect(JavaSyntaxTokenType.IDENTIFIER)) {
        if (!eatLastDot) {
          dotPos.rollbackTo()
          return refElement
        }

        typeInfo.hasErrors = true
        if (isImport) {
          error(builder, message("import.statement.identifier.or.asterisk.expected."))
        }
        else {
          error(builder, message("expected.identifier"))
        }
        dotPos.drop()
        return refElement
      }
      dotPos.drop()

      refElement = refElement.precede()

      if (parameterList) {
        typeInfo.isParameterized = parseReferenceParameterList(builder, true, diamonds)
      }
      else {
        emptyElement(builder, JavaSyntaxElementType.REFERENCE_PARAMETER_LIST)
      }
    }

    if (isStaticImport) {
      refElement.done(JavaSyntaxElementType.IMPORT_STATIC_REFERENCE)
    }
    else {
      refElement.done(JavaSyntaxElementType.JAVA_CODE_REFERENCE)
    }
    return refElement
  }

  fun parseReferenceParameterList(builder: SyntaxTreeBuilder, wildcard: Boolean, diamonds: Boolean): Boolean {
    val list = builder.mark()
    if (!builder.expect(JavaSyntaxTokenType.LT)) {
      list.done(JavaSyntaxElementType.REFERENCE_PARAMETER_LIST)
      return false
    }

    var flags = set(set(EAT_LAST_DOT, WILDCARD, wildcard), DIAMONDS, diamonds)
    var isOk = true
    while (true) {
      if (parseTypeInfo(builder, flags, true) == null) {
        error(builder, message("expected.identifier"))
      }
      else {
        val tokenType = builder.tokenType
        if (WILDCARD_KEYWORD_SET.contains(tokenType)) {
          parseReferenceList(builder, tokenType, null, JavaSyntaxTokenType.AND)
        }
      }

      if (builder.expect(JavaSyntaxTokenType.GT)) {
        break
      }
      else if (!expectOrError(builder, JavaSyntaxTokenType.COMMA, "expected.gt.or.comma")) {
        isOk = false
        break
      }
      flags = set(flags, DIAMONDS, false)
    }

    list.done(JavaSyntaxElementType.REFERENCE_PARAMETER_LIST)
    return isOk
  }

  fun parseTypeParameters(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val list = builder.mark()
    if (!builder.expect(JavaSyntaxTokenType.LT)) {
      list.done(JavaSyntaxElementType.TYPE_PARAMETER_LIST)
      return list
    }

    do {
      val param = parseTypeParameter(builder)
      if (param == null) {
        error(builder, message("expected.type.parameter"))
      }
    }
    while (builder.expect(JavaSyntaxTokenType.COMMA))

    if (!builder.expect(JavaSyntaxTokenType.GT)) {
      // hack for completion
      if (builder.tokenType === JavaSyntaxTokenType.IDENTIFIER) {
        if (builder.lookAhead(1) === JavaSyntaxTokenType.GT) {
          val errorElement = builder.mark()
          builder.advanceLexer()
          errorElement.error(message("unexpected.identifier"))
          builder.advanceLexer()
        }
        else {
          error(builder, message("expected.gt"))
        }
      }
      else {
        error(builder, message("expected.gt"))
      }
    }

    list.done(JavaSyntaxElementType.TYPE_PARAMETER_LIST)
    return list
  }

  fun parseTypeParameter(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    val param = builder.mark()

    myParser.declarationParser.parseAnnotations(builder)

    val wild = builder.expect(JavaSyntaxTokenType.QUEST)
    if (!wild && !builder.expect(JavaSyntaxTokenType.IDENTIFIER)) {
      param.rollbackTo()
      return null
    }

    parseReferenceList(builder, JavaSyntaxTokenType.EXTENDS_KEYWORD, JavaSyntaxElementType.EXTENDS_BOUND_LIST, JavaSyntaxTokenType.AND)

    if (!wild) {
      param.done(JavaSyntaxElementType.TYPE_PARAMETER)
    }
    else {
      param.error(message("error.message.wildcard.not.expected"))
    }
    return param
  }

  fun parseReferenceList(
    builder: SyntaxTreeBuilder,
    start: SyntaxElementType?,
    type: SyntaxElementType?,
    delimiter: SyntaxElementType?
  ): Boolean {
    val element = builder.mark()

    var endsWithError = false
    if (builder.expect(start)) {
      do {
        endsWithError = false
        val classReference = parseJavaCodeReference(builder, false, true, false, false)
        if (classReference == null) {
          error(builder, message("expected.identifier"))
          endsWithError = true
        }
      }
      while (builder.expect(delimiter))
    }

    if (type != null) {
      element.done(type)
    }
    else {
      element.error(message("bound.not.expected"))
    }
    return endsWithError
  }

  companion object {
    const val EAT_LAST_DOT: Int = 0x01
    const val ELLIPSIS: Int = 0x02
    const val WILDCARD: Int = 0x04
    const val DIAMONDS: Int = 0x08
    const val DISJUNCTIONS: Int = 0x10
    const val CONJUNCTIONS: Int = 0x20
    const val INCOMPLETE_ANNO: Int = 0x40
    const val VAR_TYPE: Int = 0x80

    private val WILDCARD_KEYWORD_SET: SyntaxElementTypeSet = syntaxElementTypeSetOf(JavaSyntaxTokenType.EXTENDS_KEYWORD, JavaSyntaxTokenType.SUPER_KEYWORD)
  }
}