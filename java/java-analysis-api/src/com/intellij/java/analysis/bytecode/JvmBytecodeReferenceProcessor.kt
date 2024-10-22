// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.bytecode

/**
 * Pass an instance of this interface to [JvmBytecodeAnalysis.createReferenceAnalyzer] or 
 * [JvmBytecodeAnalysis.createDeclarationAndReferencesAnalyzer] to process references in *.class files.
 */
interface JvmBytecodeReferenceProcessor {
  /**
   * Called for each reference to another class from the class being processed.
   * 
   * @param targetClass the referenced class
   * @param sourceClass the class code from which refers to [targetClass].
   */
  fun processClassReference(targetClass: JvmClassBytecodeDeclaration, sourceClass: JvmClassBytecodeDeclaration)
}