// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.bytecode

import com.intellij.java.analysis.bytecode.JvmClassBytecodeDeclaration

internal data class JvmClassBytecodeDeclarationImpl(override val binaryClassName: String) : JvmClassBytecodeDeclaration {
  override val topLevelSourceClassName: String by lazy {
    binaryClassName.substringBefore('$').replace('/', '.')
  }
}
