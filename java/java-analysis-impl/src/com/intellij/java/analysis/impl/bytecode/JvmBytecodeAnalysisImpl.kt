// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.bytecode

import com.intellij.java.analysis.bytecode.ClassFileAnalyzer
import com.intellij.java.analysis.bytecode.JvmBytecodeAnalysis
import com.intellij.java.analysis.bytecode.JvmBytecodeDeclarationProcessor
import com.intellij.java.analysis.bytecode.JvmBytecodeReferenceProcessor
import java.nio.file.Path

public class JvmBytecodeAnalysisImpl : JvmBytecodeAnalysis {
  override fun createReferenceAnalyzer(processor: JvmBytecodeReferenceProcessor): ClassFileAnalyzer {
    return ClassFileAnalyzerImpl(declarationProcessor = null, referenceProcessor = processor)
  }

  override fun createReferenceAnalyzerWithImplicitSuperclassReferences(
    processor: JvmBytecodeReferenceProcessor,
    classpath: List<Path>,
  ): ClassFileAnalyzer {
    return ClassFileAnalyzerImpl(
      declarationProcessor = null,
      referenceProcessor = processor,
      implicitAncestorReferencesResolver = ClassAncestorResolver(classpath)
    )
  }

  override fun createDeclarationAnalyzer(processor: JvmBytecodeDeclarationProcessor): ClassFileAnalyzer {
    return ClassFileAnalyzerImpl(declarationProcessor = processor, referenceProcessor = null)
  }

  override fun createDeclarationAndReferencesAnalyzer(
    declarationProcessor: JvmBytecodeDeclarationProcessor?,
    referenceProcessor: JvmBytecodeReferenceProcessor?,
  ): ClassFileAnalyzer {
    return ClassFileAnalyzerImpl(declarationProcessor, referenceProcessor)
  }
}
