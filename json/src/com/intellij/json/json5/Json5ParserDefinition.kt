// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.json5

import com.intellij.json.JSON5_FILE
import com.intellij.json.JsonParserDefinition
import com.intellij.json.psi.impl.JsonFileImpl
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType

open class Json5ParserDefinition : JsonParserDefinition() {
  
  override fun createLexer(project: Project?): Lexer {
    return Json5Lexer()
  }

  override fun createFile(fileViewProvider: FileViewProvider): PsiFile {
    return JsonFileImpl(fileViewProvider, Json5Language.INSTANCE)
  }

  override fun getFileNodeType(): IFileElementType {
    return JSON5_FILE
  }
}