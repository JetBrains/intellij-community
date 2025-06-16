// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.parser

import com.intellij.java.syntax.JavaSyntaxBundle
import com.intellij.java.syntax.element.JavaSyntaxElementType
import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.java.syntax.element.SyntaxElementTypes
import com.intellij.java.syntax.element.WhiteSpaceAndCommentSetHolder
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.i18n.ResourceBundle
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.expect

open class FileParser(private val myParser: JavaParser) {
  open fun parse(builder: SyntaxTreeBuilder) {
    parseFile(builder = builder,
              importListStopper = { b -> this.stopImportListParsing(b) },
              bundle = JavaSyntaxBundle.resourceBundle,
              errorMessageKey = "expected.class.or.interface"
    )
  }

  fun parseFile(
    builder: SyntaxTreeBuilder,
    importListStopper: (SyntaxTreeBuilder) -> Boolean,
    bundle: ResourceBundle,
    errorMessageKey: String,
  ) {
    parsePackageStatement(builder)

    val impListInfo = parseImportList(builder, importListStopper) // (importList, isEmpty)
    var firstDeclarationOk: Boolean? = null
    var firstDeclaration: SyntaxTreeBuilder.Marker? = null

    var invalidElements: SyntaxTreeBuilder.Marker? = null
    var isImplicitClass = false
    while (!builder.eof()) {
      if (builder.tokenType === JavaSyntaxTokenType.SEMICOLON) {
        builder.advanceLexer()
        continue
      }

      val declaration = myParser.moduleParser.parse(builder) ?: parseInitial(builder)
      if (declaration != null) {
        if (invalidElements != null) {
          invalidElements.errorBefore(bundle.message(errorMessageKey), declaration)
          invalidElements = null
        }
        if (firstDeclarationOk == null) {
          firstDeclarationOk = JavaParserUtil.exprType(declaration) !== JavaSyntaxElementType.MODIFIER_LIST
        }
        if (firstDeclaration == null) {
          firstDeclaration = declaration
        }
        if (!isImplicitClass && IMPLICIT_CLASS_INDICATORS.contains(JavaParserUtil.exprType(declaration))) {
          isImplicitClass = true
        }
        continue
      }

      if (invalidElements == null) {
        invalidElements = builder.mark()
      }
      builder.advanceLexer()
      if (firstDeclarationOk == null) firstDeclarationOk = false
    }

    invalidElements?.error(bundle.message(errorMessageKey))

    if (impListInfo.second && firstDeclarationOk == true) {
      impListInfo.first.setCustomEdgeTokenBinders( // pass comments behind fake import list
        left = WhiteSpaceAndCommentSetHolder.getPrecedingCommentBinder(myParser.languageLevel),
        right = null
      )
      firstDeclaration!!.setCustomEdgeTokenBinders(
        left = WhiteSpaceAndCommentSetHolder.getSpecialPrecedingCommentBinder(myParser.languageLevel),
        right = null
      )
    }
    if (isImplicitClass) {
      val beforeFirst = firstDeclaration!!.precede()
      JavaParserUtil.done(beforeFirst, JavaSyntaxElementType.IMPLICIT_CLASS, myParser.languageLevel)
    }
  }

  private fun stopImportListParsing(b: SyntaxTreeBuilder): Boolean {
    val type = b.tokenType
    if (IMPORT_LIST_STOPPER_SET.contains(type) || myParser.declarationParser.isRecordToken(b, type)) return true
    if (type === JavaSyntaxTokenType.IDENTIFIER) {
      val text = b.tokenText
      if (text == JavaKeywords.OPEN || text == JavaKeywords.MODULE) return true
    }
    return false
  }

  protected open fun parseInitial(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    return myParser.declarationParser.parse(builder, DeclarationParser.Context.FILE)
  }

  private fun parsePackageStatement(builder: SyntaxTreeBuilder) {
    val statement = builder.mark()

    if (!builder.expect(JavaSyntaxTokenType.PACKAGE_KEYWORD)) {
      val modList = builder.mark()
      myParser.declarationParser.parseAnnotations(builder)
      JavaParserUtil.done(modList, JavaSyntaxElementType.MODIFIER_LIST, myParser.languageLevel)
      if (!builder.expect(JavaSyntaxTokenType.PACKAGE_KEYWORD)) {
        statement.rollbackTo()
        return
      }
    }

    val ref = myParser.referenceParser.parseJavaCodeReference(builder, true, false, false, false)
    if (ref == null) {
      statement.error(JavaSyntaxBundle.message("expected.class.or.interface"))
      return
    }

    JavaParserUtil.semicolon(builder)

    JavaParserUtil.done(statement, JavaSyntaxElementType.PACKAGE_STATEMENT, myParser.languageLevel)
  }

  protected fun parseImportList(
    builder: SyntaxTreeBuilder,
    stopper: (SyntaxTreeBuilder) -> Boolean,
  ): Pair<SyntaxTreeBuilder.Marker, Boolean> {
    var list = builder.mark()

    var isEmpty = true
    var invalidElements: SyntaxTreeBuilder.Marker? = null
    while (!builder.eof()) {
      if (stopper(builder)) {
        break
      }
      else if (builder.tokenType === JavaSyntaxTokenType.SEMICOLON) {
        builder.advanceLexer()
        continue
      }

      val statement = parseImportStatement(builder)
      if (statement != null) {
        isEmpty = false
        if (invalidElements != null) {
          invalidElements.errorBefore(JavaSyntaxBundle.message("unexpected.token"), statement)
          invalidElements = null
        }
        continue
      }

      if (invalidElements == null) {
        invalidElements = builder.mark()
      }
      builder.advanceLexer()
    }

    invalidElements?.rollbackTo()

    if (isEmpty) {
      val precede = list.precede()
      list.rollbackTo()
      list = precede
    }

    JavaParserUtil.done(list, JavaSyntaxElementType.IMPORT_LIST, myParser.languageLevel)
    return Pair(list, isEmpty)
  }

  protected fun parseImportStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    if (builder.tokenType !== JavaSyntaxTokenType.IMPORT_KEYWORD) return null

    val statement = builder.mark()
    builder.advanceLexer()

    val identifierText = builder.tokenText
    val type: SyntaxElementType = getImportType(builder)
    val isStatic = type === JavaSyntaxElementType.IMPORT_STATIC_STATEMENT
    val isModule = type === JavaSyntaxElementType.IMPORT_MODULE_STATEMENT
    val isOk = if (isModule) {
      myParser.moduleParser.parseName(builder) != null
    }
    else {
      myParser.referenceParser.parseImportCodeReference(builder, isStatic)
    }

    //if it is `module` we should expect either `;` or `identifier`
    if (isOk && !isModule &&
        !isStatic && builder.tokenType !== JavaSyntaxTokenType.SEMICOLON &&
        identifierText == JavaKeywords.MODULE
    ) {
      JavaParserUtil.error(builder, JavaSyntaxBundle.message("expected.identifier.or.semicolon"))
    }
    else if (isOk) {
      JavaParserUtil.semicolon(builder)
    }

    JavaParserUtil.done(statement, type, myParser.languageLevel)
    return statement
  }

  private fun getImportType(builder: SyntaxTreeBuilder): SyntaxElementType {
    val type = builder.tokenType
    if (type === JavaSyntaxTokenType.STATIC_KEYWORD) {
      builder.advanceLexer()
      return JavaSyntaxElementType.IMPORT_STATIC_STATEMENT
    }
    if (type === JavaSyntaxTokenType.IDENTIFIER &&
        builder.tokenText == JavaKeywords.MODULE && builder.lookAhead(1) === JavaSyntaxTokenType.IDENTIFIER
    ) {
      builder.remapCurrentToken(JavaSyntaxTokenType.MODULE_KEYWORD)
      builder.advanceLexer()
      return JavaSyntaxElementType.IMPORT_MODULE_STATEMENT
    }
    return JavaSyntaxElementType.IMPORT_STATEMENT
  }
}

val IMPORT_LIST_STOPPER_SET: SyntaxElementTypeSet =
  SyntaxElementTypes.MODIFIER_BIT_SET + hashSetOf(JavaSyntaxTokenType.CLASS_KEYWORD,
                                                  JavaSyntaxTokenType.INTERFACE_KEYWORD,
                                                  JavaSyntaxTokenType.ENUM_KEYWORD,
                                                  JavaSyntaxTokenType.AT)

private val IMPLICIT_CLASS_INDICATORS: SyntaxElementTypeSet = syntaxElementTypeSetOf(JavaSyntaxElementType.METHOD, JavaSyntaxElementType.FIELD, JavaSyntaxElementType.CLASS_INITIALIZER)

