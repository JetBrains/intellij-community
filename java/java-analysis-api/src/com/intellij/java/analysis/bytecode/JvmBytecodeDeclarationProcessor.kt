// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.bytecode

/**
 * Pass an instance of this interface to [JvmBytecodeAnalysis.createDeclarationAnalyzer] or
 * [JvmBytecodeAnalysis.createDeclarationAndReferencesAnalyzer] to process declarations in *.class files.
 */
public interface JvmBytecodeDeclarationProcessor {
  public fun processClass(jvmClass: JvmClassBytecodeDeclaration) {
  }

  public fun processMethod(jvmMethod: JvmMethodBytecodeDeclaration) {
  }

  public fun processField(jvmField: JvmFieldBytecodeDeclaration) {
  }
}  

