// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.bytecode

import com.intellij.java.analysis.bytecode.ClassFileAnalyzer
import com.intellij.java.analysis.bytecode.JvmBytecodeAnalysis
import com.intellij.java.analysis.bytecode.JvmBytecodeDeclarationProcessor
import com.intellij.java.analysis.bytecode.JvmBytecodeReferenceProcessor

class JvmBytecodeAnalysisImpl : JvmBytecodeAnalysis {
  override fun createReferenceAnalyzer(processor: JvmBytecodeReferenceProcessor): ClassFileAnalyzer {
    return AbstractDependencyVisitor(null, processor)
  }

  override fun createDeclarationAnalyzer(processor: JvmBytecodeDeclarationProcessor): ClassFileAnalyzer {
    return AbstractDependencyVisitor(processor, null)
  }

  override fun createDeclarationAndReferencesAnalyzer(declarationProcessor: JvmBytecodeDeclarationProcessor, referenceProcessor: JvmBytecodeReferenceProcessor): ClassFileAnalyzer {
    return AbstractDependencyVisitor(declarationProcessor, referenceProcessor)
  }
}
