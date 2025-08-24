// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.java.syntax.JavaSyntaxDefinition
import com.intellij.java.syntax.element.JavaLanguageLevelProvider
import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageASTFactory
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.java.parser.BasicJavaParserUtil
import com.intellij.lang.java.parser.JavaParserUtil
import com.intellij.lang.java.syntax.JavaElementTypeConverterExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.psi.*
import com.intellij.platform.syntax.tree.SyntaxNode
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiJavaFileImpl
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.impl.source.tree.JavaASTFactory
import com.intellij.psi.tree.IFileElementType
import com.intellij.testFramework.LightVirtualFile

abstract class JavaParsingTestConfiguratorBase(
  private var level: LanguageLevel,
) : AbstractBasicJavaParsingTestConfigurator {

  override fun setUp(testCase: AbstractBasicJavaParsingTestCase) {
    val java = JavaLanguage.INSTANCE

    ElementTypeConverters.instance.let { elementTypeConverters ->
      testCase.addExplicit(elementTypeConverters, java, JavaTestElementTypeConverterExtension())
      testCase.addExplicit(elementTypeConverters, java, JavaElementTypeConverterExtension())
      testCase.clearCachesOfLanguageExtension(java, elementTypeConverters)
    }

    LanguageASTFactory.INSTANCE.let { languageASTFactory ->
      testCase.addExplicit(languageASTFactory, java, JavaASTFactory())
      testCase.clearCachesOfLanguageExtension(java, languageASTFactory)
    }

    ExtensionPointName<JavaLanguageLevelProvider>("com.intellij.java.syntax.languageLevelProvider").let { ep ->
      testCase.addExtensionPoint(ep, JavaLanguageLevelProvider::class.java)
      testCase.addExplicit(ep, JavaLanguageLevelProvider { this.languageLevel })
    }
  }

  override fun setLanguageLevel(level: LanguageLevel) {
    this.level = level
  }

  override fun getLanguageLevel(): LanguageLevel {
    return level
  }

  override fun configure(file: PsiFile) {
    file.putUserData(LanguageLevelKey.FILE_LANGUAGE_LEVEL_KEY, languageLevel)
    ourLanguageLevel = languageLevel
  }

  override fun createFileSyntaxNode(text: String, parserWrapper: BasicJavaParserUtil.ParserWrapper?): SyntaxNode {
    ourLanguageLevel = languageLevel
    return parseForSyntaxTree(text) { builder ->
      if (parserWrapper != null) {
        parseWithWrapper(builder, parserWrapper)
      }
      else {
        JavaSyntaxDefinition.parse(ourLanguageLevel, builder)
      }
    }
  }

  override fun createPsiFile(
    thinJavaParsingTestCase: AbstractBasicJavaParsingTestCase,
    name: String,
    text: String,
    parser: BasicJavaParserUtil.ParserWrapper,
  ): PsiFile {
    ourTestParser = parser

    val virtualFile = LightVirtualFile("$name.java", JavaFileType.INSTANCE, text, -1)
    val psiManager = PsiManager.getInstance(thinJavaParsingTestCase.getProject())
    val viewProvider: FileViewProvider = SingleRootFileViewProvider(psiManager, virtualFile, true)
    val file: PsiJavaFileImpl = JavaTestPsiJavaFileImpl(viewProvider)
    configure(file)
    return file
  }

}

private class MyIFileElementType : IFileElementType("test.java.file", JavaLanguage.INSTANCE) {
  override fun parseContents(chameleon: ASTNode): ASTNode? {
    val psiBuilder: PsiSyntaxBuilder = createBuilder(chameleon)
    val builder = psiBuilder.getSyntaxTreeBuilder()
    parseWithWrapper(builder, ourTestParser!!)

    val rootNode = psiBuilder.getTreeBuilt()
    return rootNode.getFirstChildNode()
  }
}

private fun createBuilder(chameleon: ASTNode?): PsiSyntaxBuilder {
  val builder = JavaParserUtil.createSyntaxBuilder(chameleon).builder
  builder.setDebugMode(true)
  return builder
}

private fun parseWithWrapper(builder: SyntaxTreeBuilder, parser: BasicJavaParserUtil.ParserWrapper) {
  val root = builder.mark()
  parser.parse(builder, ourLanguageLevel)
  if (!builder.eof()) {
    val unparsed = builder.mark()
    while (!builder.eof()) builder.advanceLexer()
    unparsed.error("Unparsed tokens")
  }
  root.done(ourSyntaxElementType)
}

private lateinit var ourLanguageLevel: LanguageLevel
private val ourSyntaxElementType = SyntaxElementType("test.java.file")
private val ourTestFileElementType: IFileElementType = MyIFileElementType()
private var ourTestParser: BasicJavaParserUtil.ParserWrapper? = null

private val converter = elementTypeConverterOf(ourSyntaxElementType to ourTestFileElementType)

private class JavaTestElementTypeConverterExtension : ElementTypeConverterFactory {
  override fun getElementTypeConverter(): ElementTypeConverter = converter
}

private class JavaTestPsiJavaFileImpl(viewProvider: FileViewProvider) : PsiJavaFileImpl(viewProvider) {
  override fun createFileElement(text: CharSequence): FileElement {
    return FileElement(ourTestFileElementType, text)
  }
}