// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.bytecode

import com.intellij.java.analysis.bytecode.ClassFileAnalyzer
import com.intellij.java.analysis.bytecode.JvmBytecodeDeclarationProcessor
import com.intellij.java.analysis.bytecode.JvmBytecodeReferenceProcessor
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

internal class ClassFileAnalyzerImpl(
  declarationProcessor: JvmBytecodeDeclarationProcessor?,
  referenceProcessor: JvmBytecodeReferenceProcessor?
) : ClassFileAnalyzer {
  private val visitor = ClassFileAnalysisVisitor(declarationProcessor, referenceProcessor)

  override fun processFile(path: Path) {
    // ASM ClassReader in any case reads the whole file into memory
    processFileContent(Files.readAllBytes(path))
  }

  @Deprecated("Use processFileContent instead", replaceWith = ReplaceWith("processFileContent(classFileContent)"))
  override fun processData(data: ByteArray) {
    processFileContent(data)
  }

  override fun processFileContent(classFileContent: ByteArray) {
    visitor.processFileContent(classFileContent)
  }

  override fun processInputStream(inputStream: InputStream) {
    processFileContent(inputStream.readAllBytes())
  }
}
