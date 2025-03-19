// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.*
import com.intellij.lexer.EmptyLexer
import com.intellij.lexer.Lexer
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import javax.swing.Icon

internal fun registerTestLanguage(testRootDisposable: Disposable) {
  (FileTypeManager.getInstance() as FileTypeManagerImpl).registerFileType(
    /* type = */ TestFileType.INSTANCE,
    /* defaultAssociations = */ listOf(ExtensionFileNameMatcher(TestFileType.INSTANCE.defaultExtension)),
    /* disposable = */ testRootDisposable,
    /* pluginDescriptor = */ PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!
  )

  LanguageParserDefinitions.INSTANCE.addExplicitExtension(
    /* key = */ TestLanguage,
    /* t = */ TestParserDefinition(),
    /* parentDisposable = */ testRootDisposable
  )
}

internal object TestLanguage : Language("TestLang")

private class TestParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?): Lexer = EmptyLexer()
  override fun createParser(project: Project?): PsiParser = TestParser()
  override fun getFileNodeType(): IFileElementType = TestFileElementType
  override fun getCommentTokens(): TokenSet = TokenSet.EMPTY
  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
  override fun createElement(node: ASTNode?): PsiElement = throw UnsupportedOperationException()
  override fun createFile(viewProvider: FileViewProvider): PsiFile = TestFile(viewProvider)
}

private class TestParser : PsiParser {
  override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
    val marker = builder.mark()
    builder.advanceLexer()
    marker.done(root)
    return builder.treeBuilt
  }
}

private class TestFileType : LanguageFileType(TestLanguage) {
  override fun getName(): String = "TestFileType"
  override fun getDescription(): String = "Test file type"
  override fun getDefaultExtension(): String = "test"
  override fun getIcon(): Icon? = null

  companion object {
    @JvmField
    val INSTANCE = TestFileType()
  }
}

internal class TestFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, TestLanguage), PsiExternalReferenceHost {
  override fun getFileType(): FileType = TestFileType.INSTANCE
}

private object TestFileElementType : IFileElementType("TesFileElementType", TestLanguage)