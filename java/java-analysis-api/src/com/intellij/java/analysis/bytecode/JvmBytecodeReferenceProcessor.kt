// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.bytecode

/**
 * Pass an instance of this interface to [JvmBytecodeAnalysis.createReferenceAnalyzer] or
 * [JvmBytecodeAnalysis.createDeclarationAndReferencesAnalyzer] to process references in *.class files.
 */
public interface JvmBytecodeReferenceProcessor {
  /**
   * Called for each reference to another class from the class being processed.
   *
   * @param targetClass the referenced class
   * @param sourceClass the class code from which refers to [targetClass].
   */
  public fun processClassReference(targetClass: JvmClassBytecodeDeclaration, sourceClass: JvmClassBytecodeDeclaration) {
  }

  /**
   * Called for each reference to a method from another class from the class being processed.
   *
   * @param targetMethod the referenced method
   * @param sourceClass the class code from which refers to [targetMethod].
   */
  public fun processMethodReference(targetMethod: JvmMethodBytecodeDeclaration, sourceClass: JvmClassBytecodeDeclaration) {
  }

  /**
   * Called for each reference to a field from another class from the class being processed.
   *
   * @param targetField the referenced field
   * @param sourceClass the class code from which refers to [targetField].
   */
  public fun processFieldReference(targetField: JvmFieldBytecodeDeclaration, sourceClass: JvmClassBytecodeDeclaration) {
  }
}