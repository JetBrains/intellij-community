// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.bytecode

import com.intellij.java.analysis.bytecode.JvmClassBytecodeDeclaration
import com.intellij.java.analysis.bytecode.JvmFieldBytecodeDeclaration
import com.intellij.java.analysis.bytecode.JvmMethodBytecodeDeclaration

internal data class JvmClassBytecodeDeclarationImpl(override val binaryClassName: String) : JvmClassBytecodeDeclaration {
  override val topLevelSourceClassName: String by lazy {
    binaryClassName.substringBefore('$').replace('/', '.')
  }
}

internal data class JvmMethodBytecodeDeclarationImpl(
  override val containingClass: JvmClassBytecodeDeclaration,
  override val name: String,
  override val descriptor: String
) : JvmMethodBytecodeDeclaration

internal data class JvmFieldBytecodeDeclarationImpl(
  override val containingClass: JvmClassBytecodeDeclaration,
  override val name: String,
  override val descriptor: String
) : JvmFieldBytecodeDeclaration