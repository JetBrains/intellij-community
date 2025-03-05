// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.bytecode

/**
 * Pass an instance of this interface to [JvmBytecodeAnalysis.createDeclarationAnalyzer] or
 * [JvmBytecodeAnalysis.createDeclarationAndReferencesAnalyzer] to process declarations in *.class files.
 */
interface JvmBytecodeDeclarationProcessor {
  fun processClass(jvmClass: JvmClassBytecodeDeclaration) {
  }

  fun processMethod(jvmMethod: JvmMethodBytecodeDeclaration) {
  }

  fun processField(jvmField: JvmFieldBytecodeDeclaration) {
  }
}  

