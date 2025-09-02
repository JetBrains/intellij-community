// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.parser

import com.intellij.java.syntax.JavaSyntaxBundle
import com.intellij.java.syntax.element.JShellSyntaxElementType
import com.intellij.java.syntax.element.JavaSyntaxElementType
import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.java.syntax.element.SyntaxElementTypes
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.pom.java.LanguageLevel
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class JShellParser(languageLevel: LanguageLevel) : JavaParser(languageLevel) {
  override val fileParser: FileParser = object : FileParser(this) {
    override fun parse(builder: SyntaxTreeBuilder) {
      parseImportList(builder) { b -> b.tokenType in IMPORT_PARSING_STOP_LIST }

      val rootClass: SyntaxTreeBuilder.Marker = builder.mark()
      try {
        while (!builder.eof()) {
          var wrapper: SyntaxTreeBuilder.Marker? = builder.mark()
          var wrapperType: SyntaxElementType? = null

          var marker: SyntaxTreeBuilder.Marker? = parseImportStatement(builder)
          if (isParsed(marker, builder) { tokenType -> tokenType === JavaSyntaxElementType.IMPORT_STATEMENT }) {
            wrapperType = JShellSyntaxElementType.IMPORT_HOLDER
          }
          else {
            marker?.rollbackTo()
            marker = declarationParser.parse(builder, DeclarationParser.Context.JSHELL)
            if (isParsed(marker, builder) { tokenType -> tokenType in TOP_LEVEL_DECLARATIONS } &&
                !builder.hasErrorsAfter(marker)
            ) {
              wrapper!!.drop() // don't need wrapper for top-level declaration
              wrapper = null
            }
            else {
              marker?.rollbackTo()
              marker = statementParser.parseStatement(builder)
              if (marker != null && !builder.hasErrorsAfter(marker)) {
                wrapperType = JShellSyntaxElementType.STATEMENTS_HOLDER
              }
              else {
                marker?.rollbackTo()
                marker = expressionParser.parse(builder)
                wrapperType = if (marker != null) JShellSyntaxElementType.STATEMENTS_HOLDER else null
              }
            }
          }

          if (marker == null) {
            wrapper!!.drop()
            break
          }

          wrapper?.done(wrapperType!!)
        }

        if (!builder.eof()) {
          builder.mark().error(JavaSyntaxBundle.message("unexpected.token"))
          while (!builder.eof()) {
            builder.advanceLexer()
          }
        }
      }
      finally {
        rootClass.done(JShellSyntaxElementType.ROOT_CLASS)
      }
    }
  }

  fun parse(builder: SyntaxTreeBuilder) {
    val root = builder.mark()
    fileParser.parse(builder)
    root.done(JShellSyntaxElementType.FILE)
  }

  @OptIn(ExperimentalContracts::class)
  private fun isParsed(
    parsedMarker: SyntaxTreeBuilder.Marker?,
    builder: SyntaxTreeBuilder,
    cond: (SyntaxElementType) -> Boolean,
  ): Boolean {
    contract {
      returns(true) implies (parsedMarker != null)
    }
    if (parsedMarker == null) return false
    val lastDone = builder.lastDoneMarker ?: return false
    return cond(lastDone.getNodeType())
  }
}

private val TOP_LEVEL_DECLARATIONS: SyntaxElementTypeSet = syntaxElementTypeSetOf(
  JavaSyntaxElementType.FIELD,
  JavaSyntaxElementType.METHOD,
  JavaSyntaxElementType.CLASS
)

private val IMPORT_PARSING_STOP_LIST: SyntaxElementTypeSet =
  IMPORT_LIST_STOPPER_SET +
  SyntaxElementTypes.MODIFIER_BIT_SET +
  SyntaxElementTypes.JAVA_COMMENT_BIT_SET +
  SyntaxElementTypes.EXPRESSION_BIT_SET +
  SyntaxElementTypes.JAVA_STATEMENT_BIT_SET +
  SyntaxElementTypes.PRIMITIVE_TYPE_BIT_SET +
  JShellSyntaxElementType.ROOT_CLASS +
  JavaSyntaxTokenType.IDENTIFIER

