// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public interface JvmBytecodeAnalysis {
  public companion object {
    @JvmStatic
    public fun getInstance(): JvmBytecodeAnalysis = service()
  }

  /**
   * Creates a new instance of [ClassFileAnalyzer] which will process references in *.class files.
   */
  @Contract(pure = true)
  public fun createReferenceAnalyzer(processor: JvmBytecodeReferenceProcessor): ClassFileAnalyzer

  /**
   * Creates a new instance of [ClassFileAnalyzer] which will process declarations in *.class files.
   */
  @Contract(pure = true)
  public fun createDeclarationAnalyzer(processor: JvmBytecodeDeclarationProcessor): ClassFileAnalyzer

  /**
   * Creates a new instance of [ClassFileAnalyzer] which will process both declarations and references in *.class files.
   */
  @Contract(pure = true)
  public fun createDeclarationAndReferencesAnalyzer(
    declarationProcessor: JvmBytecodeDeclarationProcessor?,
    referenceProcessor: JvmBytecodeReferenceProcessor?,
  ): ClassFileAnalyzer
}

/**
 * Processes *.class files using processors passed via [JvmBytecodeAnalysis]'s functions.
 *
 * The implementation is not thread-safe.
 * If you want to process multiple *.class files in parallel, create separate instances.
 */
public interface ClassFileAnalyzer {
  @Throws(IOException::class)
  public fun processFile(path: Path)

  public fun processData(data: ByteArray)

  @Throws(IOException::class)
  public fun processInputStream(inputStream: InputStream)
}