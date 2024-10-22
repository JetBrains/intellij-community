// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.bytecode

import com.intellij.openapi.components.service
import org.jetbrains.annotations.Contract
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path

/**
 * Provides methods which analyze *.class files.
 * Such analysis works faster than the regular analysis via PSI because all references are resolved but requires the modules to be compiled.
 */
interface JvmBytecodeAnalysis {
  companion object {
    @JvmStatic
    fun getInstance(): JvmBytecodeAnalysis = service()
  }

  /**
   * Creates a new instance of [ClassFileAnalyzer] which will process references in *.class files.
   */
  @Contract(pure = true)
  fun createReferenceAnalyzer(processor: JvmBytecodeReferenceProcessor): ClassFileAnalyzer

  /**
   * Creates a new instance of [ClassFileAnalyzer] which will process declarations in *.class files.
   */
  @Contract(pure = true)
  fun createDeclarationAnalyzer(processor: JvmBytecodeDeclarationProcessor): ClassFileAnalyzer

  /**
   * Creates a new instance of [ClassFileAnalyzer] which will process both declarations and references in *.class files.
   */
  @Contract(pure = true)
  fun createDeclarationAndReferencesAnalyzer(declarationProcessor: JvmBytecodeDeclarationProcessor, 
                                             referenceProcessor: JvmBytecodeReferenceProcessor): ClassFileAnalyzer
}

/**
 * Processes *.class files using processors passed via [JvmBytecodeAnalysis]'s functions.
 * 
 * The implementation is not thread-safe. 
 * If you want to process multiple *.class files in parallel, create separate instances.
 */
interface ClassFileAnalyzer {
  fun processFile(path: Path)
  
  @Throws(IOException::class)
  fun processInputStream(inputStream: InputStream)
}